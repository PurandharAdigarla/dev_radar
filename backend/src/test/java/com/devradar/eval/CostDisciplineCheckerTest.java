package com.devradar.eval;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CostDisciplineCheckerTest {

    private final CostDisciplineChecker checker = new CostDisciplineChecker(5000);

    @Test
    void shouldScorePerfectWhenUnderBudget() {
        BigDecimal score = checker.score(2000);
        assertThat(score).isEqualTo(new BigDecimal("1.000"));
    }

    @Test
    void shouldScoreZeroWhenDoubleBudget() {
        BigDecimal score = checker.score(10000);
        assertThat(score).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void shouldScorePartialWhenSlightlyOver() {
        BigDecimal score = checker.score(6000);
        assertThat(score).isBetween(new BigDecimal("0.500"), new BigDecimal("0.900"));
    }

    @Test
    void shouldScoreAcrossMultipleRadars() {
        BigDecimal score = checker.scoreMultiple(new int[]{2000, 3000, 8000});
        assertThat(score).isBetween(new BigDecimal("0.500"), new BigDecimal("0.900"));
    }
}
