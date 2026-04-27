package com.checkout.checkout_system.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final Duration TTL = Duration.ofHours(24);

    private final RedisTemplate<String, String> redisTemplate;

    public IdempotencyService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Attempts to claim the idempotency key.
     * Returns empty if the key is new (caller should proceed and store result).
     * Returns the cached response JSON if the key already exists (duplicate request).
     */
    public Optional<String> checkAndStore(String key, String responseJson) {
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(key, responseJson, TTL);
        if (Boolean.TRUE.equals(isNew)) {
            return Optional.empty();
        }
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    public Optional<String> getResponse(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }
}
