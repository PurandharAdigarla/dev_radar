package com.devradar.eval;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;

@Component
public class CostDisciplineChecker {

    private final int tokenBudget;

    public CostDisciplineChecker(@Value("${devradar.eval.token-budget:5000}") int tokenBudget) {
        this.tokenBudget = tokenBudget;
    }

    public BigDecimal score(int actualTokens) {
        if (actualTokens <= tokenBudget) return new BigDecimal("1.000");
        if (actualTokens >= tokenBudget * 2) return BigDecimal.ZERO;

        double ratio = 1.0 - ((double) (actualTokens - tokenBudget) / tokenBudget);
        return BigDecimal.valueOf(ratio).setScale(3, RoundingMode.HALF_UP);
    }

    public BigDecimal scoreMultiple(int[] tokenCounts) {
        if (tokenCounts.length == 0) return BigDecimal.ZERO;
        BigDecimal sum = Arrays.stream(tokenCounts)
                .mapToObj(this::score)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(tokenCounts.length), 3, RoundingMode.HALF_UP);
    }
}
