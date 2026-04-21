package com.devradar.apikey;

import com.devradar.domain.ApiKeyScope;
import com.devradar.domain.UserApiKey;
import com.devradar.repository.UserApiKeyRepository;
import com.devradar.security.ApiKeyGenerator;
import com.devradar.security.ApiKeyHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class ApiKeyServiceTest {

    UserApiKeyRepository repo;
    ApiKeyGenerator gen;
    ApiKeyHasher hasher;
    ApiKeyService service;

    @BeforeEach
    void setUp() {
        repo = mock(UserApiKeyRepository.class);
        gen = mock(ApiKeyGenerator.class);
        hasher = mock(ApiKeyHasher.class);
        service = new ApiKeyService(repo, gen, hasher);
    }

    @Test
    void generateReturnsRawKeyExactlyOnceAndPersistsHashAndPrefix() {
        when(gen.generate()).thenReturn("devr_rawkey12345");
        when(gen.prefix("devr_rawkey12345")).thenReturn("devr_raw");
        when(hasher.hash("devr_rawkey12345")).thenReturn("HASH");
        when(repo.save(any(UserApiKey.class))).thenAnswer(inv -> {
            UserApiKey k = inv.getArgument(0);
            k.setId(99L);
            return k;
        });

        ApiKeyService.GeneratedKey result = service.generate(42L, "Cursor", ApiKeyScope.READ);

        assertThat(result.rawKey()).isEqualTo("devr_rawkey12345");
        assertThat(result.keyPrefix()).isEqualTo("devr_raw");
        assertThat(result.id()).isEqualTo(99L);
        assertThat(result.scope()).isEqualTo(ApiKeyScope.READ);

        verify(repo).save(argThat(k ->
            "HASH".equals(k.getKeyHash()) &&
            "devr_raw".equals(k.getKeyPrefix()) &&
            "Cursor".equals(k.getName()) &&
            Long.valueOf(42L).equals(k.getUserId()) &&
            ApiKeyScope.READ == k.getScope()
        ));
    }

    @Test
    void listReturnsActiveKeysForUser() {
        UserApiKey k = new UserApiKey();
        k.setId(1L);
        k.setUserId(7L);
        k.setName("Cursor");
        k.setKeyPrefix("devr_abc");
        k.setScope(ApiKeyScope.WRITE);
        when(repo.findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(7L)).thenReturn(List.of(k));

        List<UserApiKey> out = service.list(7L);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).getName()).isEqualTo("Cursor");
    }

    @Test
    void revokeSetsRevokedAtWhenCallerOwnsKey() {
        UserApiKey k = new UserApiKey();
        k.setId(1L);
        k.setUserId(7L);
        when(repo.findById(1L)).thenReturn(Optional.of(k));
        when(repo.save(any(UserApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        service.revoke(7L, 1L);

        verify(repo).save(argThat(saved -> saved.getRevokedAt() != null));
    }

    @Test
    void revokeThrowsWhenCallerDoesNotOwnKey() {
        UserApiKey k = new UserApiKey();
        k.setId(1L);
        k.setUserId(7L);
        when(repo.findById(1L)).thenReturn(Optional.of(k));

        assertThatThrownBy(() -> service.revoke(99L, 1L))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("forbidden");
    }

    @Test
    void revokeThrowsWhenKeyNotFound() {
        when(repo.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(7L, 1L))
            .isInstanceOf(RuntimeException.class);
    }
}
