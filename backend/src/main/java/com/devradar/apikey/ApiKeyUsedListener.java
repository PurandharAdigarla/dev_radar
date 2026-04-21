package com.devradar.apikey;

import com.devradar.repository.UserApiKeyRepository;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class ApiKeyUsedListener {

    private final UserApiKeyRepository repo;

    public ApiKeyUsedListener(UserApiKeyRepository repo) {
        this.repo = repo;
    }

    @Async
    @EventListener
    @Transactional
    public void onApiKeyUsed(ApiKeyUsedEvent event) {
        repo.findById(event.keyId()).ifPresent(k -> {
            k.setLastUsedAt(Instant.now());
            repo.save(k);
        });
    }
}
