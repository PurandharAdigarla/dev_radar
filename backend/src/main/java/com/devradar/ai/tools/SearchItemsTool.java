package com.devradar.ai.tools;

import com.devradar.domain.InterestTag;
import com.devradar.domain.SourceItem;
import com.devradar.repository.InterestTagRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SearchItemsTool {

    public static final String NAME = "searchItems";
    public static final String DESCRIPTION = "Find recent ingested items matching the given interest tag slugs (case-sensitive). Returns the most recent N items.";
    public static final String INPUT_SCHEMA = """
        {
          "type": "object",
          "properties": {
            "tag_slugs": { "type": "array", "items": { "type": "string" }, "description": "Interest tag slugs like spring_boot, react" },
            "limit":     { "type": "integer", "description": "Max items to return (default 20, max 100)", "default": 20 }
          },
          "required": ["tag_slugs"]
        }
        """;

    private final InterestTagRepository tagRepo;
    private final ObjectMapper json = new ObjectMapper();

    @PersistenceContext
    private EntityManager em;

    public SearchItemsTool(InterestTagRepository tagRepo) { this.tagRepo = tagRepo; }

    public ToolDefinition definition() {
        return new ToolDefinition(NAME, DESCRIPTION, INPUT_SCHEMA);
    }

    public String execute(String inputJson) {
        try {
            var node = json.readTree(inputJson);
            List<String> slugs = new java.util.ArrayList<>();
            for (var s : node.get("tag_slugs")) slugs.add(s.asText());
            int limit = Math.min(node.has("limit") ? node.get("limit").asInt(20) : 20, 100);

            List<InterestTag> tags = tagRepo.findBySlugIn(slugs);
            if (tags.isEmpty()) return "[]";
            List<Long> tagIds = tags.stream().map(InterestTag::getId).toList();

            @SuppressWarnings("unchecked")
            List<SourceItem> items = em.createQuery(
                "SELECT DISTINCT si FROM SourceItem si, SourceItemTag sit " +
                "WHERE sit.sourceItemId = si.id AND sit.interestTagId IN :tagIds " +
                "AND si.postedAt > :cutoff " +
                "ORDER BY si.postedAt DESC")
                .setParameter("tagIds", tagIds)
                .setParameter("cutoff", java.time.Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS))
                .setMaxResults(limit)
                .getResultList();

            ArrayNode arr = json.createArrayNode();
            for (SourceItem si : items) {
                ObjectNode n = json.createObjectNode();
                n.put("id", si.getId());
                n.put("external_id", si.getExternalId());
                n.put("title", si.getTitle());
                n.put("url", si.getUrl());
                n.put("posted_at", si.getPostedAt().toString());
                arr.add(n);
            }
            return json.writeValueAsString(arr);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }
}
