package com.devradar.ingest;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class DedupService {

    private final StringRedisTemplate redis;

    public DedupService(StringRedisTemplate redis) { this.redis = redis; }

    public boolean tryAcquire(String key, Duration ttl) {
        Boolean ok = redis.opsForValue().setIfAbsent(key, "1", ttl);
        return Boolean.TRUE.equals(ok);
    }

    public void release(String key) {
        redis.delete(key);
    }
}
