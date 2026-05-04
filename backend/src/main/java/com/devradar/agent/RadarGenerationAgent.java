package com.devradar.agent;

import com.devradar.agent.RadarAgentFactory.PreviousRadar;
import com.devradar.agent.RadarAgentFactory.PreviousTheme;
import com.devradar.agent.RadarOutputParser.RadarOutput;
import com.devradar.domain.*;
import com.devradar.radar.RadarEventBus;
import com.devradar.radar.RadarService;
import com.devradar.radar.event.AgentProgressEvent;
import com.devradar.radar.event.RadarCompleteEvent;
import com.devradar.radar.event.RadarFailedEvent;
import com.devradar.radar.event.RadarStartedEvent;
import com.devradar.repository.*;
import com.google.adk.agents.RunConfig;
import com.google.adk.agents.SequentialAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.GroundingChunk;
import com.google.genai.types.GroundingMetadata;
import com.google.genai.types.Part;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RadarGenerationAgent {

    private static final Logger LOG = LoggerFactory.getLogger(RadarGenerationAgent.class);
    private static final int MAX_RETAINED_RADARS = 5;

    private final RadarAgentFactory agentFactory;
    private final RadarService radarService;
    private final RadarEventBus events;
    private final RadarRepository radarRepo;
    private final RadarThemeRepository themeRepo;
    private final RadarThemeSourceRepository sourceRepo;
    private final RadarRepoRecommendationRepository repoRecRepo;
    private final MeterRegistry meterRegistry;

    public RadarGenerationAgent(RadarAgentFactory agentFactory,
                                RadarService radarService,
                                RadarEventBus events,
                                RadarRepository radarRepo,
                                RadarThemeRepository themeRepo,
                                RadarThemeSourceRepository sourceRepo,
                                RadarRepoRecommendationRepository repoRecRepo,
                                MeterRegistry meterRegistry) {
        this.agentFactory = agentFactory;
        this.radarService = radarService;
        this.events = events;
        this.radarRepo = radarRepo;
        this.themeRepo = themeRepo;
        this.sourceRepo = sourceRepo;
        this.repoRecRepo = repoRecRepo;
        this.meterRegistry = meterRegistry;
    }

    @Async("radarGenerationExecutor")
    public void runGeneration(Long radarId, Long userId, List<String> topics) {
        long t0 = System.currentTimeMillis();
        LOG.info("starting radar generation radarId={} userId={} topics={}", radarId, userId, topics.size());

        events.publishStarted(new RadarStartedEvent(radarId));

        try {
            List<PreviousRadar> previousRadars = loadPreviousRadars(userId);
            LocalDate since = findLastRadarDate(userId);
            List<String> previousRepoUrls = loadPreviousRepoUrls(userId);

            SequentialAgent pipeline = agentFactory.buildRadarPipeline(topics, since, previousRadars, previousRepoUrls);
            InMemoryRunner runner = new InMemoryRunner(pipeline, "devradar");

            Session session = runner.sessionService()
                .createSession("devradar", String.valueOf(userId), (Map<String, Object>) null, null)
                .blockingGet();

            Content trigger = Content.fromParts(Part.fromText("Generate radar for my topics"));

            AtomicInteger tokenCount = new AtomicInteger(0);
            runner.runAsync(
                    String.valueOf(userId),
                    session.id(),
                    trigger,
                    RunConfig.builder().maxLlmCalls(30).autoCreateSession(false).build()
                )
                .doOnNext(event -> {
                    event.usageMetadata().ifPresent(usage -> {
                        usage.promptTokenCount().ifPresent(tokenCount::addAndGet);
                        usage.candidatesTokenCount().ifPresent(tokenCount::addAndGet);
                    });
                    publishProgressFromEvent(radarId, event);
                })
                .blockingSubscribe(
                    event -> {},
                    error -> LOG.error("agent pipeline error radarId={}: {}", radarId, error.getMessage(), error)
                );

            // Re-fetch session to get final state
            Session finalSession = runner.sessionService()
                .getSession("devradar", String.valueOf(userId), session.id(), java.util.Optional.empty())
                .blockingGet();

            Map<String, Object> state = finalSession.state();
            RadarOutput output = RadarOutputParser.parse(state);

            if (output.themes().isEmpty()) {
                radarService.markFailed(radarId, "NO_THEMES", "Agent produced no themes");
                events.publishFailed(new RadarFailedEvent(radarId, "NO_THEMES", "Agent produced no themes"));
                meterRegistry.counter("radar.generation", "status", "failure").increment();
                return;
            }

            persistThemes(radarId, output.themes());
            persistRepos(radarId, output.repos());

            long elapsed = System.currentTimeMillis() - t0;
            radarService.markReady(radarId, elapsed, tokenCount.get(), 0, 0);
            events.publishComplete(new RadarCompleteEvent(radarId, elapsed, tokenCount.get()));
            meterRegistry.counter("radar.generation", "status", "success").increment();
            LOG.info("radar generation complete radarId={} elapsed={}ms themes={} tokens={}",
                    radarId, elapsed, output.themes().size(), tokenCount.get());

            purgeOldRadars(userId);
        } catch (Exception e) {
            LOG.error("radar generation failed radarId={}: {}", radarId, e.getMessage(), e);
            radarService.markFailed(radarId, "GENERATION_FAILED", e.getMessage());
            events.publishFailed(new RadarFailedEvent(radarId, "GENERATION_FAILED", e.getMessage()));
            meterRegistry.counter("radar.generation", "status", "failure").increment();
        }
    }

    private void publishProgressFromEvent(Long radarId, Event event) {
        try {
            String author = event.author();
            if (author == null) return;

            String phase;
            if (author.startsWith("research_")) {
                phase = "research";
            } else if (author.startsWith("repos_")) {
                phase = "repo_discovery";
            } else {
                return;
            }

            List<String> queries = new ArrayList<>();
            List<AgentProgressEvent.SearchResult> results = new ArrayList<>();

            event.groundingMetadata().ifPresent(gm -> {
                gm.webSearchQueries().ifPresent(queries::addAll);
                gm.groundingChunks().ifPresent(chunks -> {
                    for (GroundingChunk chunk : chunks) {
                        chunk.web().ifPresent(web -> {
                            String title = web.title().orElse("");
                            String domain = web.domain().orElse("");
                            String url = web.uri().orElse("");
                            if (!title.isBlank() || !domain.isBlank()) {
                                results.add(new AgentProgressEvent.SearchResult(title, domain, url));
                            }
                        });
                    }
                });
            });

            if (queries.isEmpty() && results.isEmpty()) return;

            events.publishProgress(new AgentProgressEvent(radarId, author, phase, queries, results));
        } catch (Exception e) {
            LOG.debug("Failed to publish progress for radarId={}: {}", radarId, e.getMessage());
        }
    }

    private void persistThemes(Long radarId, List<RadarOutputParser.ThemeOutput> themes) {
        for (RadarOutputParser.ThemeOutput t : themes) {
            RadarTheme theme = new RadarTheme();
            theme.setRadarId(radarId);
            theme.setTopic(truncate(t.topic(), 80));
            theme.setTitle(truncate(t.title(), 200));
            theme.setSummary(t.summary());
            theme.setDisplayOrder(t.displayOrder());
            theme = themeRepo.save(theme);

            for (RadarOutputParser.SourceOutput src : t.sources()) {
                if (isValidUrl(src.url())) {
                    RadarThemeSource source = new RadarThemeSource();
                    source.setThemeId(theme.getId());
                    source.setUrl(truncate(src.url(), 2000));
                    source.setTitle(truncate(src.title(), 500));
                    sourceRepo.save(source);
                }
            }
        }
    }

    private void persistRepos(Long radarId, List<RadarOutputParser.RepoOutput> repos) {
        for (RadarOutputParser.RepoOutput r : repos) {
            if (!isValidUrl(r.repoUrl())) continue;
            if (r.whyNotable() == null || r.whyNotable().isBlank()) continue;
            RadarRepoRecommendation rec = new RadarRepoRecommendation();
            rec.setRadarId(radarId);
            rec.setTopic(truncate(r.topic(), 80));
            rec.setRepoUrl(truncate(r.repoUrl(), 500));
            rec.setRepoName(truncate(r.repoName(), 200));
            rec.setDescription(truncate(r.description(), 1000));
            rec.setWhyNotable(truncate(r.whyNotable(), 1000));
            rec.setCategory(validateCategory(r.category()));
            rec.setDisplayOrder(r.displayOrder());
            repoRecRepo.save(rec);
        }
    }

    private static boolean isValidUrl(String url) {
        return url != null && !url.isBlank()
                && (url.startsWith("https://") || url.startsWith("http://"))
                && url.length() <= 2000;
    }

    private static String truncate(String val, int maxLen) {
        if (val == null) return "";
        return val.length() <= maxLen ? val : val.substring(0, maxLen);
    }

    private static final java.util.Set<String> VALID_CATEGORIES = java.util.Set.of(
            "mcp-server", "agent-skill", "agent-framework", "dev-tool", "prompt-library");

    private static String validateCategory(String category) {
        if (category != null && VALID_CATEGORIES.contains(category.toLowerCase())) {
            return category.toLowerCase();
        }
        return "dev-tool";
    }

    private List<String> loadPreviousRepoUrls(Long userId) {
        var page = radarRepo.findByUserIdOrderByGeneratedAtDesc(userId, PageRequest.of(0, MAX_RETAINED_RADARS));
        List<Long> radarIds = page.getContent().stream()
                .filter(r -> r.getStatus() == RadarStatus.READY)
                .map(Radar::getId)
                .toList();
        if (radarIds.isEmpty()) return List.of();
        return repoRecRepo.findByRadarIdIn(radarIds).stream()
                .map(RadarRepoRecommendation::getRepoUrl)
                .distinct()
                .toList();
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
                    .map(t -> new PreviousTheme(
                        t.getTopic() != null ? t.getTopic() : t.getTitle(),
                        t.getTitle(),
                        t.getSummary()))
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
            radarRepo.delete(old);
            LOG.info("purged old radar id={} for userId={}", old.getId(), userId);
        }
    }
}
