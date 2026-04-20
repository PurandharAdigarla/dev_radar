package com.devradar.eval;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CitationCheckerTest {

    private final CitationChecker checker = new CitationChecker();

    @Test
    void shouldScorePerfectWhenAllClaimsAreCited() {
        var theme = buildTheme(
                "Spring Boot 3.5 brings virtual thread support, improving throughput.",
                List.of(buildItem("Spring Boot 3.5 Released with Virtual Threads Support",
                        "https://spring.io/blog/spring-boot-3-5"))
        );

        BigDecimal score = checker.score(List.of(theme));
        assertThat(score).isGreaterThanOrEqualTo(new BigDecimal("0.800"));
    }

    @Test
    void shouldScoreLowWhenNoCitations() {
        var theme = buildTheme(
                "Spring Boot 3.5 brings virtual thread support.",
                List.of()
        );

        BigDecimal score = checker.score(List.of(theme));
        assertThat(score).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldScoreAcrossMultipleThemes() {
        var theme1 = buildTheme("Kotlin coroutines improve async performance.",
                List.of(buildItem("Kotlin Coroutines Performance Guide", "https://a.com")));
        var theme2 = buildTheme("No sources.", List.of());

        BigDecimal score = checker.score(List.of(theme1, theme2));
        assertThat(score).isBetween(new BigDecimal("0.400"), new BigDecimal("0.600"));
    }

    private CitationChecker.ThemeWithItems buildTheme(String summary, List<CitationChecker.CitedItem> items) {
        return new CitationChecker.ThemeWithItems(summary, items);
    }

    private CitationChecker.CitedItem buildItem(String title, String url) {
        return new CitationChecker.CitedItem(title, url);
    }
}
