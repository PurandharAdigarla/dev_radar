package com.devradar.ai.tools;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ToolRegistry {

    private final SearchItemsTool search;
    private final ScoreRelevanceTool score;
    private final GetItemDetailTool detail;

    public ToolRegistry(SearchItemsTool search, ScoreRelevanceTool score, GetItemDetailTool detail) {
        this.search = search; this.score = score; this.detail = detail;
    }

    public List<ToolDefinition> definitions() {
        return List.of(search.definition(), score.definition(), detail.definition());
    }

    /** Dispatch a tool call by name. Returns the JSON string the tool produced (errors included as {"error":...}). */
    public String dispatch(String name, String inputJson) {
        return switch (name) {
            case SearchItemsTool.NAME -> search.execute(inputJson);
            case ScoreRelevanceTool.NAME -> score.execute(inputJson);
            case GetItemDetailTool.NAME -> detail.execute(inputJson);
            default -> "{\"error\":\"unknown tool: " + name + "\"}";
        };
    }
}
