package com.devradar.eval;

import com.devradar.domain.*;
import com.devradar.repository.EvalRunRepository;
import com.devradar.repository.EvalScoreRepository;
import com.devradar.repository.RadarRepository;
import com.devradar.repository.RadarThemeRepository;
import com.devradar.repository.RadarThemeItemRepository;
import com.devradar.repository.SourceItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvalServiceTest {

    @Mock private RadarRepository radarRepository;
    @Mock private RadarThemeRepository themeRepository;
    @Mock private RadarThemeItemRepository themeItemRepository;
    @Mock private SourceItemRepository sourceItemRepository;
    @Mock private EvalRunRepository evalRunRepository;
    @Mock private EvalScoreRepository evalScoreRepository;
    @Mock private CitationChecker citationChecker;
    @Mock private CostDisciplineChecker costDisciplineChecker;
    @Mock private LlmJudge llmJudge;

    @InjectMocks
    private EvalService evalService;

    @Test
    void shouldCreateEvalRunAndPersistScores() {
        var radar = buildRadar(1L, 3000);
        var theme = buildTheme(1L, "Spring Boot updates", "Spring Boot 3.5 released with virtual threads.");

        when(radarRepository.findByStatusOrderByGeneratedAtDesc(eq(RadarStatus.READY), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(radar)));
        when(themeRepository.findByRadarId(1L)).thenReturn(List.of(theme));
        when(themeItemRepository.findByThemeId(anyLong())).thenReturn(List.of());
        when(evalRunRepository.save(any(EvalRun.class))).thenAnswer(inv -> {
            var run = inv.getArgument(0, EvalRun.class);
            run.setId(100L);
            return run;
        });
        when(evalScoreRepository.save(any(EvalScore.class))).thenAnswer(inv -> inv.getArgument(0));

        when(citationChecker.score(anyList())).thenReturn(new BigDecimal("0.900"));
        when(costDisciplineChecker.scoreMultiple(any(int[].class))).thenReturn(new BigDecimal("1.000"));
        when(llmJudge.scoreRelevance(anyList(), anyList())).thenReturn(new BigDecimal("0.850"));
        when(llmJudge.scoreDistinctness(anyList())).thenReturn(new BigDecimal("0.800"));

        EvalRun result = evalService.runEval(10);

        assertThat(result.getStatus()).isEqualTo(EvalRunStatus.COMPLETED);
        verify(evalScoreRepository, times(4)).save(any(EvalScore.class));
    }

    @Test
    void shouldHandleEmptyRadarsGracefully() {
        when(radarRepository.findByStatusOrderByGeneratedAtDesc(eq(RadarStatus.READY), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(evalRunRepository.save(any(EvalRun.class))).thenAnswer(inv -> {
            var run = inv.getArgument(0, EvalRun.class);
            run.setId(101L);
            return run;
        });

        EvalRun result = evalService.runEval(10);

        assertThat(result.getStatus()).isEqualTo(EvalRunStatus.COMPLETED);
        assertThat(result.getRadarCount()).isZero();
    }

    private Radar buildRadar(Long id, int tokenCount) {
        var radar = new Radar();
        radar.setId(id);
        radar.setUserId(1L);
        radar.setStatus(RadarStatus.READY);
        radar.setTokenCount(tokenCount);
        radar.setGenerationMs(4000L);
        radar.setGeneratedAt(Instant.now());
        radar.setPeriodStart(Instant.now().minusSeconds(604800));
        radar.setPeriodEnd(Instant.now());
        return radar;
    }

    private RadarTheme buildTheme(Long radarId, String title, String summary) {
        var theme = new RadarTheme();
        theme.setId(1L);
        theme.setRadarId(radarId);
        theme.setTitle(title);
        theme.setSummary(summary);
        theme.setDisplayOrder(1);
        return theme;
    }
}
