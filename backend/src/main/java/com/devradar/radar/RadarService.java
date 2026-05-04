package com.devradar.radar;

import com.devradar.domain.Radar;
import com.devradar.domain.RadarStatus;
import com.devradar.repository.RadarRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class RadarService {

    private static final Logger LOG = LoggerFactory.getLogger(RadarService.class);

    private final RadarRepository repo;
    public RadarService(RadarRepository repo) { this.repo = repo; }

    @PostConstruct
    @Transactional
    public void failStaleGenerations() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.MINUTES);
        List<Radar> stale = repo.findByStatusAndPeriodEndBefore(RadarStatus.GENERATING, cutoff);
        for (Radar r : stale) {
            r.setStatus(RadarStatus.FAILED);
            r.setErrorCode("STALE_GENERATION");
            r.setErrorMessage("Generation abandoned — instance restarted or timed out");
            repo.save(r);
            LOG.info("marked stale radar {} as FAILED", r.getId());
        }
        if (!stale.isEmpty()) {
            LOG.info("cleaned up {} stale GENERATING radars", stale.size());
        }
    }

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
    public void markReady(Long radarId, long generationMs, int tokenCount, int inputTokens, int outputTokens) {
        Radar r = repo.findById(radarId).orElseThrow();
        r.setStatus(RadarStatus.READY);
        r.setGeneratedAt(Instant.now());
        r.setGenerationMs(generationMs);
        r.setTokenCount(tokenCount);
        r.setInputTokenCount(inputTokens);
        r.setOutputTokenCount(outputTokens);
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
