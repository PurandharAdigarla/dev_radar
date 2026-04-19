package com.devradar.web.rest;

import com.devradar.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class InterestTagResourceIT extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void list_returnsSeededTags() throws Exception {
        mvc.perform(get("/api/interest-tags?size=100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[?(@.slug=='spring_boot')]").exists())
            .andExpect(jsonPath("$.content[?(@.slug=='react')]").exists());
    }

    @Test
    void list_filtersByCategory() throws Exception {
        mvc.perform(get("/api/interest-tags?category=language&size=100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[?(@.slug=='java')]").exists())
            .andExpect(jsonPath("$.content[?(@.slug=='spring_boot')]").doesNotExist());
    }

    @Test
    void list_searchByQuery() throws Exception {
        mvc.perform(get("/api/interest-tags?q=spring&size=100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[?(@.slug=='spring_boot')]").exists())
            .andExpect(jsonPath("$.content[?(@.slug=='react')]").doesNotExist());
    }
}
