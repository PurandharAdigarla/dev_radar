package com.devradar.eval;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.EvalRun;
import com.devradar.domain.EvalRunStatus;
import com.devradar.domain.EvalScore;
import com.devradar.domain.EvalScoreCategory;
import com.devradar.repository.EvalRunRepository;
import com.devradar.repository.EvalScoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvalRepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private EvalRunRepository evalRunRepository;

    @Autowired
    private EvalScoreRepository evalScoreRepository;

    @Test
    void shouldPersistEvalRunAndScores() {
        var run = new EvalRun();
        run.setStatus(EvalRunStatus.RUNNING);
        run.setRadarCount(5);
        run.setStartedAt(Instant.now());
        run = evalRunRepository.save(run);

        assertThat(run.getId()).isNotNull();
        assertThat(run.getCreatedAt()).isNotNull();

        var score = new EvalScore();
        score.setEvalRunId(run.getId());
        score.setCategory(EvalScoreCategory.RELEVANCE);
        score.setScore(new BigDecimal("0.850"));
        score.setDetails("{\"avg\": 0.85, \"cases\": 5}");
        score = evalScoreRepository.save(score);

        assertThat(score.getId()).isNotNull();

        List<EvalScore> scores = evalScoreRepository.findByEvalRunId(run.getId());
        assertThat(scores).hasSize(1);
        assertThat(scores.getFirst().getCategory()).isEqualTo(EvalScoreCategory.RELEVANCE);
    }

    @Test
    void shouldFindRecentEvalRuns() {
        var run = new EvalRun();
        run.setStatus(EvalRunStatus.COMPLETED);
        run.setRadarCount(3);
        run.setStartedAt(Instant.now().minusSeconds(60));
        run.setCompletedAt(Instant.now());
        evalRunRepository.save(run);

        var runs = evalRunRepository.findAllByOrderByCreatedAtDesc();
        assertThat(runs).isNotEmpty();
        assertThat(runs.getFirst().getStatus()).isEqualTo(EvalRunStatus.COMPLETED);
    }
}
