package com.devradar.apikey;

import com.devradar.domain.ApiKeyScope;
import com.devradar.domain.UserApiKey;
import com.devradar.repository.UserApiKeyRepository;
import com.devradar.security.ApiKeyGenerator;
import com.devradar.security.ApiKeyHasher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ApiKeyService {

    private final UserApiKeyRepository repo;
    private final ApiKeyGenerator generator;
    private final ApiKeyHasher hasher;

    public ApiKeyService(UserApiKeyRepository repo, ApiKeyGenerator generator, ApiKeyHasher hasher) {
        this.repo = repo;
        this.generator = generator;
        this.hasher = hasher;
    }

    public record GeneratedKey(Long id, String rawKey, String keyPrefix, ApiKeyScope scope, String name) {}

    @Transactional
    public GeneratedKey generate(Long userId, String name, ApiKeyScope scope) {
        String raw = generator.generate();
        String prefix = generator.prefix(raw);
        String hash = hasher.hash(raw);

        UserApiKey k = new UserApiKey();
        k.setUserId(userId);
        k.setName(name);
        k.setKeyHash(hash);
        k.setKeyPrefix(prefix);
        k.setScope(scope);
        UserApiKey saved = repo.save(k);

        return new GeneratedKey(saved.getId(), raw, prefix, scope, name);
    }

    public List<UserApiKey> list(Long userId) {
        return repo.findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public void revoke(Long userId, Long keyId) {
        UserApiKey k = repo.findById(keyId).orElseThrow(() -> new RuntimeException("not found"));
        if (!k.getUserId().equals(userId)) throw new RuntimeException("forbidden");
        k.setRevokedAt(Instant.now());
        repo.save(k);
    }
}
