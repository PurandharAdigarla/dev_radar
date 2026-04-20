package com.devradar.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GoldenDatasetLoaderTest {

    private final GoldenDatasetLoader loader = new GoldenDatasetLoader();

    @Test
    void shouldLoadAllGoldenCases() {
        List<GoldenRadarCase> cases = loader.loadAll();

        assertThat(cases).hasSizeGreaterThanOrEqualTo(2);
        assertThat(cases.getFirst().name()).contains("Java");
        assertThat(cases.getFirst().userInterests()).contains("java", "spring_boot");
        assertThat(cases.getFirst().sourceItems()).isNotEmpty();
        assertThat(cases.getFirst().expectedThemes()).isNotEmpty();
    }

    @Test
    void shouldParseSourceItemsCorrectly() {
        var cases = loader.loadAll();
        var firstItem = cases.getFirst().sourceItems().getFirst();

        assertThat(firstItem.id()).isEqualTo(1001);
        assertThat(firstItem.title()).isNotBlank();
        assertThat(firstItem.url()).startsWith("https://");
        assertThat(firstItem.tags()).isNotEmpty();
    }

    @Test
    void shouldParseExpectedThemesCorrectly() {
        var cases = loader.loadAll();
        var firstTheme = cases.getFirst().expectedThemes().getFirst();

        assertThat(firstTheme.title()).isNotBlank();
        assertThat(firstTheme.expectedItemIds()).isNotEmpty();
    }
}
