package com.devradar.eval;

import java.util.List;

public record GoldenRadarCase(
        String name,
        List<String> userInterests,
        List<SourceItemFixture> sourceItems,
        List<ExpectedTheme> expectedThemes,
        int tokenBudget
) {
    public record SourceItemFixture(
            long id,
            String title,
            String url,
            List<String> tags,
            String postedAt
    ) {}

    public record ExpectedTheme(
            String title,
            List<Long> expectedItemIds,
            List<Long> shouldNotInclude
    ) {}
}
