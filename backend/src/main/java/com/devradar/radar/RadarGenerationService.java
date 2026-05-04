package com.devradar.radar;

import com.devradar.ai.AiProviderException;
import com.devradar.ai.AiSummaryCache;
import com.devradar.ai.RadarOrchestrator;
import com.devradar.ai.AiResponse;
import com.devradar.ai.RadarOrchestrator.PreviousRadarSummary;
import com.devradar.ai.RadarOrchestrator.PreviousTheme;
import com.devradar.domain.Radar;
import com.devradar.domain.RadarStatus;
import com.devradar.domain.RadarTheme;
import com.devradar.domain.RadarThemeItem;
import com.devradar.domain.RadarWebSource;
import com.devradar.repository.RadarRepository;
import com.devradar.repository.RadarThemeItemRepository;
import com.devradar.repository.RadarThemeRepository;
import com.devradar.repository.RadarWebSourceRepository;
import com.devradar.radar.event.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class RadarGenerationService {

    private static final Logger LOG = LoggerFactory.getLogger(RadarGenerationService.class);

    private static final int PREVIOUS_RADAR_COUNT = 3;
    private static final DateTimeFormatter WEEK_LABEL = DateTimeFormatter.ofPattern("MMM d");

    private final RadarOrchestrator orchestrator;
    private final RadarService radarService;
    private final RadarRepository radarRepo;
    private final RadarThemeRepository themeRepo;
    private final RadarThemeItemRepository themeItemRepo;
    private final RadarWebSourceRepository webSourceRepo;
    private final AiSummaryCache cache;
    private final RadarEventBus events;
    private final MeterRegistry meterRegistry;

    public RadarGenerationService(
        RadarOrchestrator orchestrator,
        RadarService radarService,
        RadarRepository radarRepo,
        RadarThemeRepository themeRepo,
        RadarThemeItemRepository themeItemRepo,
        RadarWebSourceRepository webSourceRepo,
        AiSummaryCache cache,
        RadarEventBus events,
        MeterRegistry meterRegistry
    ) {
        this.orchestrator = orchestrator;
        this.radarService = radarService;
        this.radarRepo = radarRepo;
        this.themeRepo = themeRepo;
        this.themeItemRepo = themeItemRepo;
        this.webSourceRepo = webSourceRepo;
        this.cache = cache;
        this.events = events;
        this.meterRegistry = meterRegistry;
    }

    /** Fired by RadarApplicationService.create(). Runs asynchronously. */
    @Async("radarGenerationExecutor")
    public void runGeneration(Long radarId, Long userId, List<String> userInterests, List<Long> candidateIds) {
        long t0 = System.currentTimeMillis();
        events.publishStarted(new RadarStartedEvent(radarId));
        var sample = Timer.start(meterRegistry);
        try {
            if (candidateIds.isEmpty()) {
                throw new IllegalStateException(
                    "No source items found matching your interests in the last 7 days. "
                    + "Try adding more interest tags or check back after ingestion runs.");
            }
            List<PreviousRadarSummary> previousRadars = loadPreviousRadars(userId);
            var result = orchestrator.generate(userInterests, candidateIds,
                    new com.devradar.ai.tools.ToolContext(userId, radarId), userId, previousRadars);
            sample.stop(Timer.builder("radar.generation.duration").register(meterRegistry));
            meterRegistry.counter("radar.generation", "status", "success").increment();
            persistAndStream(radarId, result.themes());
            persistWebSources(radarId, result.webSources());
            long elapsed = System.currentTimeMillis() - t0;
            int tokens = result.totalInputTokens() + result.totalOutputTokens();
            radarService.markReady(radarId, elapsed, tokens, result.totalInputTokens(), result.totalOutputTokens());
            events.publishComplete(new RadarCompleteEvent(radarId, elapsed, tokens));
        } catch (AiProviderException e) {
            meterRegistry.counter("radar.generation", "status", "failure").increment();
            LOG.error("radar generation failed radar={}: {}", radarId, e.toString(), e);
            radarService.markFailed(radarId, "AI_PROVIDER_ERROR", e.getMessage());
            events.publishFailed(new RadarFailedEvent(radarId, "AI_PROVIDER_ERROR", e.getMessage()));
        } catch (Exception e) {
            meterRegistry.counter("radar.generation", "status", "failure").increment();
            String errorCode = (e instanceof IllegalStateException && e.getMessage() != null && e.getMessage().contains("No source items"))
                    ? "NO_SOURCE_ITEMS" : "GENERATION_FAILED";
            LOG.error("radar generation failed radar={}: {}", radarId, e.toString(), e);
            radarService.markFailed(radarId, errorCode, e.getMessage());
            events.publishFailed(new RadarFailedEvent(radarId, errorCode, e.getMessage()));
        }
    }

    private void persistWebSources(Long radarId, List<AiResponse.GroundingSource> sources) {
        if (sources == null || sources.isEmpty()) return;
        sources.stream().distinct().forEach(src -> {
            RadarWebSource ws = new RadarWebSource();
            ws.setRadarId(radarId);
            ws.setUrl(src.uri());
            ws.setTitle(src.title());
            webSourceRepo.save(ws);
        });
        LOG.info("persisted {} web sources for radar={}", sources.size(), radarId);
    }

    private List<PreviousRadarSummary> loadPreviousRadars(Long userId) {
        try {
            var page = radarRepo.findByUserIdOrderByGeneratedAtDesc(userId, PageRequest.of(0, PREVIOUS_RADAR_COUNT + 1));
            List<Radar> readyRadars = page.getContent().stream()
                    .filter(r -> r.getStatus() == RadarStatus.READY)
                    .limit(PREVIOUS_RADAR_COUNT)
                    .toList();

            List<PreviousRadarSummary> summaries = new ArrayList<>();
            for (Radar radar : readyRadars) {
                String label = radar.getPeriodStart().atZone(ZoneOffset.UTC).format(WEEK_LABEL)
                        + " – " + radar.getPeriodEnd().atZone(ZoneOffset.UTC).format(WEEK_LABEL);

                List<RadarTheme> themes = themeRepo.findByRadarIdOrderByDisplayOrderAsc(radar.getId());
                List<PreviousTheme> prevThemes = themes.stream()
                        .map(t -> new PreviousTheme(t.getTitle(), t.getSummary()))
                        .toList();
                summaries.add(new PreviousRadarSummary(label, prevThemes));
            }
            LOG.info("loaded {} previous radars for cross-week context (userId={})", summaries.size(), userId);
            return summaries;
        } catch (Exception e) {
            LOG.warn("failed to load previous radars for userId={}: {}", userId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Persist themes + items and publish SSE events. NOT @Transactional — Spring's proxy is bypassed
     * when called from same-class runGeneration(). Each repo.save() runs in its own auto-tx (Spring
     * Data JPA default). Acceptable for ingestion-style writes; partial failure leaves an orphan
     * theme/items pair, which we accept rather than complicating the design.
     */
    private void persistAndStream(Long radarId, List<RadarOrchestrator.RadarOrchestrationTheme> themes) {
        for (var t : themes) {
            String summary = cache.get(t.itemIds()).orElseGet(() -> {
                cache.put(t.itemIds(), t.summary());
                return t.summary();
            });

            RadarTheme theme = new RadarTheme();
            theme.setRadarId(radarId);
            theme.setTitle(t.title());
            theme.setSummary(summary);
            theme.setDisplayOrder(t.displayOrder());
            theme = themeRepo.save(theme);

            int order = 0;
            for (Long itemId : t.itemIds()) {
                RadarThemeItem rti = new RadarThemeItem();
                rti.setThemeId(theme.getId());
                rti.setSourceItemId(itemId);
                rti.setDisplayOrder(order++);
                themeItemRepo.save(rti);
            }

            events.publishThemeComplete(new ThemeCompleteEvent(radarId, theme.getId(), theme.getTitle(), theme.getSummary(), t.itemIds(), theme.getDisplayOrder()));
        }
    }
}
