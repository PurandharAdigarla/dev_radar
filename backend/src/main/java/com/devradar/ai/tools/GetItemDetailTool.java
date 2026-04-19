package com.devradar.ai.tools;

import com.devradar.repository.SourceItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class GetItemDetailTool {

    public static final String NAME = "getItemDetail";
    public static final String DESCRIPTION = "Get the full details (title, url, author, posted_at, raw_payload) of one source_item by id.";
    public static final String INPUT_SCHEMA = """
        {
          "type": "object",
          "properties": {
            "id": { "type": "integer", "description": "source_items.id" }
          },
          "required": ["id"]
        }
        """;

    private final SourceItemRepository repo;
    private final ObjectMapper json = new ObjectMapper();

    public GetItemDetailTool(SourceItemRepository repo) { this.repo = repo; }

    public ToolDefinition definition() {
        return new ToolDefinition(NAME, DESCRIPTION, INPUT_SCHEMA);
    }

    public String execute(String inputJson) {
        try {
            long id = json.readTree(inputJson).get("id").asLong();
            var maybe = repo.findById(id);
            if (maybe.isEmpty()) return "{\"error\":\"item not found: " + id + "\"}";
            var si = maybe.get();
            ObjectNode n = json.createObjectNode();
            n.put("id", si.getId());
            n.put("external_id", si.getExternalId());
            n.put("title", si.getTitle());
            n.put("url", si.getUrl());
            n.put("author", si.getAuthor());
            n.put("posted_at", si.getPostedAt().toString());
            if (si.getRawPayload() != null) n.set("raw_payload", json.readTree(si.getRawPayload()));
            return json.writeValueAsString(n);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }
}
