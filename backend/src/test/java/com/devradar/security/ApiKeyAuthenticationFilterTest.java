package com.devradar.security;

import com.devradar.apikey.ApiKeyUsedEvent;
import com.devradar.domain.ApiKeyScope;
import com.devradar.domain.UserApiKey;
import com.devradar.repository.UserApiKeyRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApiKeyAuthenticationFilterTest {

    UserApiKeyRepository repo;
    ApiKeyHasher hasher;
    ApplicationEventPublisher events;
    FilterChain chain;
    ApiKeyAuthenticationFilter filter;
    MeterRegistry metersMock;

    @BeforeEach
    void setUp() {
        repo = mock(UserApiKeyRepository.class);
        hasher = mock(ApiKeyHasher.class);
        events = mock(ApplicationEventPublisher.class);
        chain = mock(FilterChain.class);
        metersMock = new SimpleMeterRegistry();
        filter = new ApiKeyAuthenticationFilter(repo, hasher, events, metersMock);
        SecurityContextHolder.clearContext();
    }

    @Test
    void skipsWhenPathNotMcp() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/users/me");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        verifyNoInteractions(repo);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void rejectsMcpRequestWithoutAuthorization() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp/message");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void rejectsMcpRequestWithJwtStyleToken() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp/message");
        req.addHeader("Authorization", "Bearer eyJabc.jwt.token");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void rejectsMcpRequestWithUnknownKey() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp/message");
        req.addHeader("Authorization", "Bearer devr_nope");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        when(hasher.hash("devr_nope")).thenReturn("hashval");
        when(repo.findByKeyHashAndRevokedAtIsNull("hashval")).thenReturn(Optional.empty());

        filter.doFilter(req, resp, chain);

        assertThat(resp.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void setsAuthenticationAndPublishesEventOnValidKey() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp/message");
        req.addHeader("Authorization", "Bearer devr_ok");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        UserApiKey entity = new UserApiKey();
        entity.setId(42L);
        entity.setUserId(7L);
        entity.setScope(ApiKeyScope.WRITE);
        entity.setKeyHash("hashok");

        when(hasher.hash("devr_ok")).thenReturn("hashok");
        when(repo.findByKeyHashAndRevokedAtIsNull("hashok")).thenReturn(Optional.of(entity));

        filter.doFilter(req, resp, chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isInstanceOf(ApiKeyAuthenticationToken.class);
        ApiKeyAuthenticationToken tok = (ApiKeyAuthenticationToken) auth;
        assertThat(tok.getUserId()).isEqualTo(7L);
        assertThat(tok.getKeyId()).isEqualTo(42L);
        assertThat(tok.getScope()).isEqualTo(ApiKeyScope.WRITE);

        ArgumentCaptor<ApiKeyUsedEvent> evt = ArgumentCaptor.forClass(ApiKeyUsedEvent.class);
        verify(events).publishEvent(evt.capture());
        assertThat(evt.getValue().keyId()).isEqualTo(42L);
        verify(chain).doFilter(req, resp);
    }
}
