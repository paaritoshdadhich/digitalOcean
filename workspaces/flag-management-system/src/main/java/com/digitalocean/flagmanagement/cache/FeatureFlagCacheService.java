package com.digitalocean.flagmanagement.cache;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Cache-aside helpers for flag configuration and evaluation results.
 * <p>
 * <b>Bounded staleness:</b> evaluation keys include {@code flagVersion}, so after a committed
 * write bumps the version and deletes {@code flag:{name}}, new requests resolve against the new
 * version and naturally miss old {@code eval:*} entries—no Redis {@code KEYS}/SCAN invalidation.
 * In-flight readers that loaded a pre-commit snapshot may still populate a stale
 * {@code flag:{name}} or an {@code eval:*} key for the old version until the 60s TTL expires.
 * Redis outages are treated as cache misses; evaluation continues from PostgreSQL.
 */
@Service
public class FeatureFlagCacheService {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagCacheService.class);
    private static final Duration TTL = Duration.ofSeconds(60);

    private final RedisTemplate<String, Object> redisTemplate;

    public FeatureFlagCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<CachedFlagConfig> getFlagConfig(String flagName) {
        return get(FeatureFlagCacheKeys.flagConfig(flagName), CachedFlagConfig.class);
    }

    public void putFlagConfig(CachedFlagConfig config) {
        put(FeatureFlagCacheKeys.flagConfig(config.name()), config);
    }

    public void evictFlagConfig(String flagName) {
        try {
            redisTemplate.delete(FeatureFlagCacheKeys.flagConfig(flagName));
        } catch (RuntimeException ex) {
            log.warn("Redis unavailable while evicting flag config for '{}': {}", flagName, ex.toString());
        }
    }

    public Optional<CachedEvaluation> getEvaluation(UUID flagId, long flagVersion, String userId) {
        return get(FeatureFlagCacheKeys.evaluation(flagId, flagVersion, userId), CachedEvaluation.class);
    }

    public void putEvaluation(UUID flagId, long flagVersion, String userId, CachedEvaluation evaluation) {
        put(FeatureFlagCacheKeys.evaluation(flagId, flagVersion, userId), evaluation);
    }

    private <T> Optional<T> get(String key, Class<T> type) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            if (!type.isInstance(value)) {
                log.warn("Unexpected cache payload type for key {}: {}", key, value.getClass().getName());
                return Optional.empty();
            }
            return Optional.of(type.cast(value));
        } catch (RuntimeException ex) {
            log.warn("Redis unavailable while reading key '{}': {}", key, ex.toString());
            return Optional.empty();
        }
    }

    private void put(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value, TTL);
        } catch (RuntimeException ex) {
            log.warn("Redis unavailable while writing key '{}': {}", key, ex.toString());
        }
    }
}
