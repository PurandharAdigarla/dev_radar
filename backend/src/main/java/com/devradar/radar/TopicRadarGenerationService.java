package com.devradar.radar;

import com.devradar.ai.AiResponse;
import com.devradar.domain.*;
import com.devradar.radar.TopicRadarOrchestrator.*;
import com.devradar.radar.event.*;
import com.devradar.repository.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class TopicRadarGenerationService {

    private static final Logger LOG = LoggerFactory.getLogger(TopicRadarGenerationService.class);
    private static final int MAX_RETAINED_RADARS = 5;

    private final TopicRadarOrchestrator orchestrator;
    private final RadarService radarService;
    private final RadarRepository radarRepo;
    private final RadarThemeRepository themeRepo;
    private final RadarThemeItemRepository themeItemRepo;
    private final RadarWebSourceRepository webSourceRepo;
    private final RadarEventBus events;
    private final MeterRegistry meterRegistry;

    public TopicRadarGenerationService(
            TopicRadarOrchestrator orchestrator,
            RadarService radarService,
            RadarRepository radarRepo,
            RadarThemeRepository themeRepo,
            RadarThemeItemRepository themeItemRepo,
            RadarWebSourceRepository webSourceRepo,
            RadarEventBus events,
            MeterRegistry meterRegistry) {
        this.orchestrator = orchestrator;
        this.radarService = radarService;
        this.radarRepo = radarRepo;
        this.themeRepo = themeRepo;
        this.themeItemRepo = themeItemRepo;
        this.webSourceRepo = webSourceRepo;
        this.events = events;
        this.meterRegistry = meterRegistry;
    }

    @Async("radarGenerationExecutor")
    public void runGeneration(Long radarId, Long userId, List<String> topics) {
        long t0 = System.currentTimeMillis();
        events.publishStarted(new RadarStartedEvent(radarId));
        var sample = Timer.start(meterRegistry);

        try {
            List<PreviousRadar> previousRadars = loadPreviousRadars(userId);
            LocalDate since = findLastRadarDate(userId);

            TopicRadarResult result = orchestrator.generate(topics, previousRadars, since);
            sample.stop(Timer.builder("radar.generation.duration").register(meterRegistry));
            meterRegistry.counter("radar.generation", "status", "success").increment();

            persistThemes(radarId, result.themes());
            persistWebSources(radarId, result.webSources(), result.themes());

            long elapsed = System.currentTimeMillis() - t0;
            int tokens = result.inputTokens() + result.outputTokens();
            radarService.markReady(radarId, elapsed, tokens, result.inputTokens(), result.outputTokens());
            events.publishComplete(new RadarCompleteEvent(radarId, elapsed, tokens));

            purgeOldRadars(userId);
        } catch (Exception e) {
            meterRegistry.counter("radar.generation", "status", "failure").increment();
            LOG.error("topic radar generation failed radar={}: {}", radarId, e.toString(), e);
            radarService.markFailed(radarId, "GENERATION_FAILED", e.getMessage());
            events.publishFailed(new RadarFailedEvent(radarId, "GENERATION_FAILED", e.getMessage()));
        }
    }

    private void persistThemes(Long radarId, List<TopicTheme> themes) {
        int order = 0;
        for (TopicTheme t : themes) {
            RadarTheme theme = new RadarTheme();
            theme.setRadarId(radarId);
            theme.setTitle(t.title());
            theme.setSummary(t.summary());
            theme.setDisplayOrder(order++);
            theme = themeRepo.save(theme);

            events.publishThemeComplete(new ThemeCompleteEvent(
                    radarId, theme.getId(), theme.getTitle(), theme.getSummary(), List.of(), theme.getDisplayOrder()));
        }
    }

    private void persistWebSources(Long radarId, List<AiResponse.GroundingSource> globalSources,
                                   List<TopicTheme> themes) {
        var allSources = new ArrayList<>(globalSources);
        for (var theme : themes) {
            allSources.addAll(theme.sources());
        }
        allSources.stream()
                .filter(s -> s.uri() != null && !s.uri().isBlank())
                .distinct()
                .forEach(src -> {
                    RadarWebSource ws = new RadarWebSource();
                    ws.setRadarId(radarId);
                    ws.setUrl(src.uri());
                    ws.setTitle(src.title());
                    webSourceRepo.save(ws);
                });
    }

    private List<PreviousRadar> loadPreviousRadars(Long userId) {
        var page = radarRepo.findByUserIdOrderByGeneratedAtDesc(userId, PageRequest.of(0, MAX_RETAINED_RADARS));
        List<PreviousRadar> result = new ArrayList<>();

        for (Radar radar : page.getContent()) {
            if (radar.getStatus() != RadarStatus.READY) continue;
            String date = radar.getGeneratedAt() != null
                    ? radar.getGeneratedAt().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE)
                    : "unknown";
            List<RadarTheme> themes = themeRepo.findByRadarIdOrderByDisplayOrderAsc(radar.getId());
            List<PreviousTheme> prevThemes = themes.stream()
                    .map(t -> new PreviousTheme(extractTopic(t), t.getTitle(), t.getSummary()))
                    .toList();
            result.add(new PreviousRadar(date, prevThemes));
        }
        return result;
    }

    private LocalDate findLastRadarDate(Long userId) {
        var page = radarRepo.findByUserIdOrderByGeneratedAtDesc(userId, PageRequest.of(0, 1));
        return page.getContent().stream()
                .filter(r -> r.getStatus() == RadarStatus.READY && r.getGeneratedAt() != null)
                .findFirst()
                .map(r -> r.getGeneratedAt().atZone(ZoneOffset.UTC).toLocalDate())
                .orElse(null);
    }

    private void purgeOldRadars(Long userId) {
        var page = radarRepo.findByUserIdOrderByGeneratedAtDesc(userId, PageRequest.of(0, 100));
        List<Radar> all = page.getContent();
        if (all.size() <= MAX_RETAINED_RADARS) return;

        List<Radar> toDelete = all.subList(MAX_RETAINED_RADARS, all.size());
        for (Radar old : toDelete) {
            webSourceRepo.findByRadarId(old.getId()).forEach(webSourceRepo::delete);
            var themes = themeRepo.findByRadarId(old.getId());
            for (var theme : themes) {
                themeItemRepo.findByThemeId(theme.getId()).forEach(themeItemRepo::delete);
            }
            themes.forEach(themeRepo::delete);
            radarRepo.delete(old);
            LOG.info("purged old radar id={} for userId={}", old.getId(), userId);
        }
    }

    private static String extractTopic(RadarTheme theme) {
        return theme.getTitle();
    }
}
