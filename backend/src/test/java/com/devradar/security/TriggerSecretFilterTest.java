package com.devradar.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TriggerSecretFilterTest {

    FilterChain chain;
    TriggerSecretFilter filter;

    @BeforeEach
    void setUp() {
        chain = mock(FilterChain.class);
        filter = new TriggerSecretFilter("my-secret");
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsRequestWithCorrectSecret() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/internal/ingest/rss");
        req.addHeader("X-Trigger-Secret", "my-secret");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .anyMatch(a -> "ROLE_INTERNAL".equals(a.getAuthority()));
    }

    @Test
    void blocksRequestWithWrongSecret() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/internal/ingest/rss");
        req.addHeader("X-Trigger-Secret", "wrong-secret");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void blocksRequestWithNoSecretHeader() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/internal/ingest/rss");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void skipsNonInternalPaths() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/radars");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
    }
}
