package com.devradar.ai.tools;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.SourceItem;
import com.devradar.domain.SourceItemTag;
import com.devradar.repository.InterestTagRepository;
import com.devradar.repository.SourceItemRepository;
import com.devradar.repository.SourceItemTagRepository;
import com.devradar.repository.SourceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SearchItemsToolTest extends AbstractIntegrationTest {

    @Autowired SearchItemsTool tool;
    @Autowired SourceRepository sources;
    @Autowired SourceItemRepository items;
    @Autowired SourceItemTagRepository sit;
    @Autowired InterestTagRepository tags;
    @Autowired ObjectMapper json;

    @Test
    void searchByTagSlugs_returnsItemsTaggedWithAny() throws Exception {
        var hn = sources.findByCode("HN").orElseThrow();
        var spring = tags.findBySlug("spring_boot").orElseThrow();
        var react = tags.findBySlug("react").orElseThrow();

        SourceItem a = newItem(hn.getId(), "search-1", "Spring Boot 3.5");
        SourceItem b = newItem(hn.getId(), "search-2", "React 19 hooks");
        items.save(a); items.save(b);
        sit.save(new SourceItemTag(a.getId(), spring.getId()));
        sit.save(new SourceItemTag(b.getId(), react.getId()));

        String input = """
            {"tag_slugs": ["spring_boot"], "limit": 10}
            """;
        String resultJson = tool.execute(input);

        JsonNode arr = json.readTree(resultJson);
        assertThat(arr.isArray()).isTrue();
        assertThat(arr.size()).isGreaterThanOrEqualTo(1);
        boolean foundSpring = false;
        for (JsonNode n : arr) if ("search-1".equals(n.get("external_id").asText())) foundSpring = true;
        assertThat(foundSpring).isTrue();
    }

    private SourceItem newItem(Long sourceId, String extId, String title) {
        SourceItem si = new SourceItem();
        si.setSourceId(sourceId);
        si.setExternalId(extId);
        si.setUrl("https://example.com/" + extId);
        si.setTitle(title);
        si.setPostedAt(Instant.now());
        return si;
    }
}
