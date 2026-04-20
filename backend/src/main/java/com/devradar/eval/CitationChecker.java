package com.devradar.eval;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class CitationChecker {

    public record CitedItem(String title, String url) {}
    public record ThemeWithItems(String summary, List<CitedItem> items) {}

    public BigDecimal score(List<ThemeWithItems> themes) {
        if (themes.isEmpty()) return BigDecimal.ZERO;

        BigDecimal total = BigDecimal.ZERO;
        for (var theme : themes) {
            total = total.add(scoreTheme(theme));
        }
        return total.divide(BigDecimal.valueOf(themes.size()), 3, RoundingMode.HALF_UP);
    }

    private BigDecimal scoreTheme(ThemeWithItems theme) {
        if (theme.items().isEmpty()) return BigDecimal.ZERO;

        int matched = 0;
        String summaryLower = theme.summary().toLowerCase();
        for (var item : theme.items()) {
            String[] titleWords = item.title().toLowerCase().split("\\s+");
            int significantWords = 0;
            int matchedWords = 0;
            for (String word : titleWords) {
                if (word.length() > 3) {
                    significantWords++;
                    if (summaryLower.contains(word)) {
                        matchedWords++;
                    }
                }
            }
            if (significantWords > 0 && (double) matchedWords / significantWords >= 0.3) {
                matched++;
            }
        }

        return BigDecimal.valueOf(matched)
                .divide(BigDecimal.valueOf(theme.items().size()), 3, RoundingMode.HALF_UP);
    }
}
