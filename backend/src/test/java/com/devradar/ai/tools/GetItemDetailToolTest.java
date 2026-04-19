package com.devradar.ai.tools;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.SourceItem;
import com.devradar.repository.SourceItemRepository;
import com.devradar.repository.SourceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GetItemDetailToolTest extends AbstractIntegrationTest {

    @Autowired GetItemDetailTool tool;
    @Autowired SourceItemRepository items;
    @Autowired SourceRepository sources;
    @Autowired ObjectMapper json;

    @Test
    void execute_returnsItemFields() throws Exception {
        var hn = sources.findByCode("HN").orElseThrow();
        SourceItem si = new SourceItem();
        si.setSourceId(hn.getId());
        si.setExternalId("detail-1");
        si.setUrl("https://example.com/detail-1");
        si.setTitle("Detail item");
        si.setAuthor("alice");
        si.setPostedAt(Instant.now());
        si.setRawPayload("{\"k\":\"v\"}");
        items.save(si);

        String input = "{\"id\": " + si.getId() + "}";
        String result = tool.execute(input);

        JsonNode n = json.readTree(result);
        assertThat(n.get("title").asText()).isEqualTo("Detail item");
        assertThat(n.get("url").asText()).contains("detail-1");
        assertThat(n.get("author").asText()).isEqualTo("alice");
    }

    @Test
    void execute_unknownId_returnsError() throws Exception {
        String result = tool.execute("{\"id\": 999999}");
        JsonNode n = json.readTree(result);
        assertThat(n.has("error")).isTrue();
    }
}
