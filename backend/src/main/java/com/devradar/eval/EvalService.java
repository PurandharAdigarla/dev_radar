package com.devradar.eval;

import com.devradar.domain.*;
import com.devradar.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class EvalService {

    private static final Logger log = LoggerFactory.getLogger(EvalService.class);

    private final RadarRepository radarRepository;
    private final RadarThemeRepository themeRepository;
    private final RadarThemeItemRepository themeItemRepository;
    private final SourceItemRepository sourceItemRepository;
    private final EvalRunRepository evalRunRepository;
    private final EvalScoreRepository evalScoreRepository;
    private final CitationChecker citationChecker;
    private final CostDisciplineChecker costDisciplineChecker;
    private final LlmJudge llmJudge;

    public EvalService(RadarRepository radarRepository,
                       RadarThemeRepository themeRepository,
                       RadarThemeItemRepository themeItemRepository,
                       SourceItemRepository sourceItemRepository,
                       EvalRunRepository evalRunRepository,
                       EvalScoreRepository evalScoreRepository,
                       CitationChecker citationChecker,
                       CostDisciplineChecker costDisciplineChecker,
                       LlmJudge llmJudge) {
        this.radarRepository = radarRepository;
        this.themeRepository = themeRepository;
        this.themeItemRepository = themeItemRepository;
        this.sourceItemRepository = sourceItemRepository;
        this.evalRunRepository = evalRunRepository;
        this.evalScoreRepository = evalScoreRepository;
        this.citationChecker = citationChecker;
        this.costDisciplineChecker = costDisciplineChecker;
        this.llmJudge = llmJudge;
    }

    @Transactional
    public EvalRun runEval(int radarCount) {
        var run = new EvalRun();
        run.setStatus(EvalRunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        run.setRadarCount(0);
        run = evalRunRepository.save(run);

        try {
            var radars = radarRepository.findByStatusOrderByGeneratedAtDesc(
                    RadarStatus.READY, PageRequest.of(0, radarCount)).getContent();
            run.setRadarCount(radars.size());

            if (radars.isEmpty()) {
                run.setStatus(EvalRunStatus.COMPLETED);
                run.setCompletedAt(Instant.now());
                return evalRunRepository.save(run);
            }

            List<CitationChecker.ThemeWithItems> allThemesWithItems = new ArrayList<>();
            List<String> allThemeTitles = new ArrayList<>();
            List<String> allUserInterests = new ArrayList<>();
            int[] tokenCounts = radars.stream().mapToInt(Radar::getTokenCount).toArray();

            for (var radar : radars) {
                var themes = themeRepository.findByRadarId(radar.getId());
                for (var theme : themes) {
                    allThemeTitles.add(theme.getTitle());
                    var themeItems = themeItemRepository.findByThemeId(theme.getId());
                    var citedItems = themeItems.stream()
                            .map(ti -> {
                                var si = sourceItemRepository.findById(ti.getSourceItemId()).orElse(null);
                                if (si == null) return new CitationChecker.CitedItem("", "");
                                return new CitationChecker.CitedItem(si.getTitle(), si.getUrl());
                            })
                            .toList();
                    allThemesWithItems.add(new CitationChecker.ThemeWithItems(theme.getSummary(), citedItems));
                }
            }

            BigDecimal citationScore = citationChecker.score(allThemesWithItems);
            persistScore(run.getId(), EvalScoreCategory.CITATIONS, citationScore);

            BigDecimal costScore = costDisciplineChecker.scoreMultiple(tokenCounts);
            persistScore(run.getId(), EvalScoreCategory.COST_DISCIPLINE, costScore);

            BigDecimal relevanceScore = llmJudge.scoreRelevance(allUserInterests, allThemeTitles);
            persistScore(run.getId(), EvalScoreCategory.RELEVANCE, relevanceScore);

            BigDecimal distinctnessScore = llmJudge.scoreDistinctness(allThemeTitles);
            persistScore(run.getId(), EvalScoreCategory.DISTINCTNESS, distinctnessScore);

            run.setStatus(EvalRunStatus.COMPLETED);
            run.setCompletedAt(Instant.now());
            log.info("eval run {} completed: citations={} cost={} relevance={} distinctness={}",
                    run.getId(), citationScore, costScore, relevanceScore, distinctnessScore);

        } catch (Exception e) {
            log.error("eval run {} failed: {}", run.getId(), e.getMessage(), e);
            run.setStatus(EvalRunStatus.FAILED);
            run.setErrorMessage(e.getMessage());
            run.setCompletedAt(Instant.now());
        }

        return evalRunRepository.save(run);
    }

    private void persistScore(Long runId, EvalScoreCategory category, BigDecimal score) {
        var evalScore = new EvalScore();
        evalScore.setEvalRunId(runId);
        evalScore.setCategory(category);
        evalScore.setScore(score);
        evalScoreRepository.save(evalScore);
    }
}
