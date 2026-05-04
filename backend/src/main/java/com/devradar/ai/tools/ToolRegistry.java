package com.devradar.ai.tools;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ToolRegistry {

    private final SearchItemsTool search;
    private final GetItemDetailTool detail;

    public ToolRegistry(SearchItemsTool search, GetItemDetailTool detail) {
        this.search = search; this.detail = detail;
    }

    public List<ToolDefinition> definitions() {
        return List.of(search.definition(), detail.definition());
    }

    public String dispatch(String name, String inputJson, ToolContext ctx) {
        return switch (name) {
            case SearchItemsTool.NAME -> search.execute(inputJson);
            case GetItemDetailTool.NAME -> detail.execute(inputJson);
            default -> "{\"error\":\"unknown tool: " + name + "\"}";
        };
    }
}
