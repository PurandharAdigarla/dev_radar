package com.devradar.repository;

import com.devradar.AbstractIntegrationTest;
import com.devradar.domain.ApiKeyScope;
import com.devradar.domain.User;
import com.devradar.domain.UserApiKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserApiKeyRepositoryIT extends AbstractIntegrationTest {

    @Autowired UserApiKeyRepository repo;
    @Autowired UserRepository userRepo;

    @Test
    void findsByHashOnlyWhenNotRevoked() {
        User u = new User();
        u.setEmail("apikey1@test.com");
        u.setDisplayName("Api Tester");
        u.setPasswordHash("hash");
        u.setActive(true);
        u = userRepo.save(u);

        UserApiKey active = new UserApiKey();
        active.setUserId(u.getId());
        active.setName("Cursor");
        active.setKeyHash("hash-active");
        active.setKeyPrefix("devr_aaa");
        active.setScope(ApiKeyScope.READ);
        repo.save(active);

        UserApiKey revoked = new UserApiKey();
        revoked.setUserId(u.getId());
        revoked.setName("Old");
        revoked.setKeyHash("hash-revoked");
        revoked.setKeyPrefix("devr_bbb");
        revoked.setScope(ApiKeyScope.READ);
        revoked.setRevokedAt(Instant.now());
        repo.save(revoked);

        assertThat(repo.findByKeyHashAndRevokedAtIsNull("hash-active")).isPresent();
        assertThat(repo.findByKeyHashAndRevokedAtIsNull("hash-revoked")).isEmpty();
    }

    @Test
    void listsActiveKeysForUserOrderedByCreatedAtDesc() throws InterruptedException {
        User u = new User();
        u.setEmail("apikey2@test.com");
        u.setDisplayName("Api Tester 2");
        u.setPasswordHash("hash");
        u.setActive(true);
        u = userRepo.save(u);

        UserApiKey k1 = new UserApiKey();
        k1.setUserId(u.getId());
        k1.setName("First");
        k1.setKeyHash("h1");
        k1.setKeyPrefix("devr_111");
        k1.setScope(ApiKeyScope.READ);
        repo.save(k1);

        // MySQL TIMESTAMP has 1s precision; separate the inserts so the DESC ordering is deterministic.
        Thread.sleep(1100);

        UserApiKey k2 = new UserApiKey();
        k2.setUserId(u.getId());
        k2.setName("Second");
        k2.setKeyHash("h2");
        k2.setKeyPrefix("devr_222");
        k2.setScope(ApiKeyScope.WRITE);
        repo.save(k2);

        var active = repo.findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(u.getId());
        assertThat(active).hasSize(2);
        assertThat(active.get(0).getName()).isEqualTo("Second");
    }
}
