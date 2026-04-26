package com.devradar.ingest;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class DedupService {

    @Nullable
    private final StringRedisTemplate redis;

    public DedupService(@Nullable StringRedisTemplate redis) { this.redis = redis; }

    public boolean tryAcquire(String key, Duration ttl) {
        if (redis == null) return true;
        Boolean ok = redis.opsForValue().setIfAbsent(key, "1", ttl);
        return Boolean.TRUE.equals(ok);
    }

    public void release(String key) {
        if (redis == null) return;
        redis.delete(key);
    }
}
