package com.devradar.ai.tools;

import com.devradar.repository.SourceItemRepository;
import com.devradar.repository.SourceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class GetItemDetailTool {

    public static final String NAME = "getItemDetail";
    public static final String DESCRIPTION = "Get the full details (title, description, url, author, source_name, posted_at) of one source_item by id.";
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
    private final SourceRepository sourceRepo;
    private final ObjectMapper json = new ObjectMapper();
    private final ConcurrentHashMap<Long, String> sourceNameCache = new ConcurrentHashMap<>();

    public GetItemDetailTool(SourceItemRepository repo, SourceRepository sourceRepo) {
        this.repo = repo;
        this.sourceRepo = sourceRepo;
    }

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
            n.put("title", si.getTitle());
            n.put("description", si.getDescription());
            n.put("url", si.getUrl());
            n.put("author", si.getAuthor());
            n.put("source_name", resolveSourceName(si.getSourceId()));
            n.put("posted_at", si.getPostedAt().toString());
            return json.writeValueAsString(n);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    private String resolveSourceName(Long sourceId) {
        return sourceNameCache.computeIfAbsent(sourceId, sid ->
            sourceRepo.findById(sid).map(s -> s.getCode()).orElse("UNKNOWN")
        );
    }
}
