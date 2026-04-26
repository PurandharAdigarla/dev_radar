package com.devradar.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RateLimitService {

    private static final Logger LOG = LoggerFactory.getLogger(RateLimitService.class);

    @Nullable
    private final StringRedisTemplate redis;

    public RateLimitService(@Nullable StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean tryConsume(String key, int maxRequests, Duration window) {
        if (redis == null) return true;
        try {
            String redisKey = "rate:" + key;
            Long count = redis.opsForValue().increment(redisKey);
            if (count == null) return true;
            if (count == 1L) {
                redis.expire(redisKey, window);
            }
            return count <= maxRequests;
        } catch (Exception e) {
            LOG.warn("Redis rate-limit check failed, allowing request: {}", e.getMessage());
            return true;
        }
    }

    public long remainingRequests(String key, int maxRequests) {
        if (redis == null) return maxRequests;
        try {
            String redisKey = "rate:" + key;
            String val = redis.opsForValue().get(redisKey);
            if (val == null) return maxRequests;
            long used = Long.parseLong(val);
            return Math.max(0, maxRequests - used);
        } catch (Exception e) {
            LOG.warn("Redis rate-limit remaining check failed: {}", e.getMessage());
            return maxRequests;
        }
    }
}
