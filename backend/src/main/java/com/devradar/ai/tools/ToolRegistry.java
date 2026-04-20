package com.devradar.ai.tools;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ToolRegistry {

    private final SearchItemsTool search;
    private final ScoreRelevanceTool score;
    private final GetItemDetailTool detail;
    private final CheckRepoForVulnerabilityTool repoCheck;

    public ToolRegistry(SearchItemsTool search, ScoreRelevanceTool score, GetItemDetailTool detail, CheckRepoForVulnerabilityTool repoCheck) {
        this.search = search; this.score = score; this.detail = detail; this.repoCheck = repoCheck;
    }

    public List<ToolDefinition> definitions() {
        return List.of(search.definition(), score.definition(), detail.definition(), repoCheck.definition());
    }

    public String dispatch(String name, String inputJson, ToolContext ctx) {
        return switch (name) {
            case SearchItemsTool.NAME -> search.execute(inputJson);
            case ScoreRelevanceTool.NAME -> score.execute(inputJson);
            case GetItemDetailTool.NAME -> detail.execute(inputJson);
            case CheckRepoForVulnerabilityTool.NAME -> repoCheck.execute(inputJson, ctx);
            default -> "{\"error\":\"unknown tool: " + name + "\"}";
        };
    }
}
