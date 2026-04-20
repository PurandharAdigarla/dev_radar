package com.devradar.radar;

import com.devradar.ai.AiSummaryCache;
import com.devradar.ai.RadarOrchestrator;
import com.devradar.domain.RadarTheme;
import com.devradar.domain.RadarThemeItem;
import com.devradar.repository.RadarThemeItemRepository;
import com.devradar.repository.RadarThemeRepository;
import com.devradar.radar.event.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RadarGenerationService {

    private static final Logger LOG = LoggerFactory.getLogger(RadarGenerationService.class);

    private final RadarOrchestrator orchestrator;
    private final RadarService radarService;
    private final RadarThemeRepository themeRepo;
    private final RadarThemeItemRepository themeItemRepo;
    private final AiSummaryCache cache;
    private final RadarEventBus events;
    private final com.devradar.repository.ActionProposalRepository actionProposalRepo;
    private final MeterRegistry meterRegistry;

    public RadarGenerationService(
        RadarOrchestrator orchestrator,
        RadarService radarService,
        RadarThemeRepository themeRepo,
        RadarThemeItemRepository themeItemRepo,
        AiSummaryCache cache,
        RadarEventBus events,
        com.devradar.repository.ActionProposalRepository actionProposalRepo,
        MeterRegistry meterRegistry
    ) {
        this.orchestrator = orchestrator;
        this.radarService = radarService;
        this.themeRepo = themeRepo;
        this.themeItemRepo = themeItemRepo;
        this.cache = cache;
        this.events = events;
        this.actionProposalRepo = actionProposalRepo;
        this.meterRegistry = meterRegistry;
    }

    /** Fired by RadarApplicationService.create(). Runs asynchronously. */
    @Async("radarGenerationExecutor")
    public void runGeneration(Long radarId, Long userId, List<String> userInterests, List<Long> candidateIds) {
        long t0 = System.currentTimeMillis();
        events.publishStarted(new RadarStartedEvent(radarId));
        var sample = Timer.start(meterRegistry);
        try {
            var result = orchestrator.generate(userInterests, candidateIds, new com.devradar.ai.tools.ToolContext(userId, radarId));
            sample.stop(Timer.builder("radar.generation.duration").register(meterRegistry));
            meterRegistry.counter("radar.generation", "status", "success").increment();
            persistAndStream(radarId, result.themes());
            for (var prop : actionProposalRepo.findByRadarIdOrderByCreatedAtAsc(radarId)) {
                events.publishActionProposed(new com.devradar.radar.event.ActionProposedEvent(
                    radarId, prop.getId(), prop.getKind().name(), prop.getPayload()));
            }
            long elapsed = System.currentTimeMillis() - t0;
            int tokens = result.totalInputTokens() + result.totalOutputTokens();
            radarService.markReady(radarId, elapsed, tokens, result.totalInputTokens(), result.totalOutputTokens());
            events.publishComplete(new RadarCompleteEvent(radarId, elapsed, tokens));
        } catch (Exception e) {
            meterRegistry.counter("radar.generation", "status", "failure").increment();
            LOG.error("radar generation failed radar={}: {}", radarId, e.toString(), e);
            radarService.markFailed(radarId, "GENERATION_FAILED", e.getMessage());
            events.publishFailed(new RadarFailedEvent(radarId, "GENERATION_FAILED", e.getMessage()));
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
