package com.devradar.radar;

import com.devradar.domain.Radar;
import com.devradar.domain.RadarStatus;
import com.devradar.repository.RadarRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class RadarService {

    private final RadarRepository repo;
    public RadarService(RadarRepository repo) { this.repo = repo; }

    @Transactional
    public Radar createPending(Long userId) {
        Radar r = new Radar();
        r.setUserId(userId);
        r.setPeriodEnd(Instant.now());
        r.setPeriodStart(Instant.now().minus(7, ChronoUnit.DAYS));
        r.setStatus(RadarStatus.GENERATING);
        return repo.save(r);
    }

    @Transactional
    public void markReady(Long radarId, long generationMs, int tokenCount) {
        Radar r = repo.findById(radarId).orElseThrow();
        r.setStatus(RadarStatus.READY);
        r.setGeneratedAt(Instant.now());
        r.setGenerationMs(generationMs);
        r.setTokenCount(tokenCount);
        repo.save(r);
    }

    @Transactional
    public void markFailed(Long radarId, String errorCode, String message) {
        Radar r = repo.findById(radarId).orElseThrow();
        r.setStatus(RadarStatus.FAILED);
        r.setErrorCode(errorCode);
        r.setErrorMessage(message);
        repo.save(r);
    }
}
