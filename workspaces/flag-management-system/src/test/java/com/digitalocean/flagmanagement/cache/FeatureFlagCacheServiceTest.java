package com.digitalocean.flagmanagement.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Unit tests for Redis-unavailable fallback behavior in the cache-aside layer.
 */
@ExtendWith(MockitoExtension.class)
class FeatureFlagCacheServiceTest {

    private static final UUID FLAG_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String FLAG_NAME = "redis_down";
    private static final String USER_ID = "user-1";

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider;

    private FeatureFlagCacheService cacheService;

    @BeforeEach
    void setUp() {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        cacheService = new FeatureFlagCacheService(redisTemplateProvider);
    }

    @Test
    void getFlagConfig_whenRedisThrows_returnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(FeatureFlagCacheKeys.flagConfig(FLAG_NAME)))
                .thenThrow(new RuntimeException("Redis unavailable"));

        assertThat(cacheService.getFlagConfig(FLAG_NAME)).isEmpty();
    }

    @Test
    void getEvaluation_whenRedisThrows_returnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(FeatureFlagCacheKeys.evaluation(FLAG_ID, 1L, USER_ID)))
                .thenThrow(new RuntimeException("Redis unavailable"));

        assertThat(cacheService.getEvaluation(FLAG_ID, 1L, USER_ID)).isEmpty();
    }

    @Test
    void putFlagConfig_whenRedisThrows_doesNotPropagate() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("Redis unavailable"))
                .when(valueOperations)
                .set(anyString(), any(), any(Duration.class));

        CachedFlagConfig config = new CachedFlagConfig(FLAG_ID, FLAG_NAME, true, 1L);
        cacheService.putFlagConfig(config);

        verify(valueOperations).set(
                eq(FeatureFlagCacheKeys.flagConfig(FLAG_NAME)),
                eq(config),
                eq(Duration.ofSeconds(60))
        );
    }

    @Test
    void putEvaluation_whenRedisThrows_doesNotPropagate() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("Redis unavailable"))
                .when(valueOperations)
                .set(anyString(), any(), any(Duration.class));

        cacheService.putEvaluation(FLAG_ID, 2L, USER_ID, new CachedEvaluation(true, "GLOBAL"));

        verify(valueOperations).set(
                eq(FeatureFlagCacheKeys.evaluation(FLAG_ID, 2L, USER_ID)),
                eq(new CachedEvaluation(true, "GLOBAL")),
                eq(Duration.ofSeconds(60))
        );
    }

    @Test
    void evictFlagConfig_whenRedisThrows_doesNotPropagate() {
        when(redisTemplate.delete(FeatureFlagCacheKeys.flagConfig(FLAG_NAME)))
                .thenThrow(new RuntimeException("Redis unavailable"));

        cacheService.evictFlagConfig(FLAG_NAME);

        verify(redisTemplate).delete(FeatureFlagCacheKeys.flagConfig(FLAG_NAME));
    }

    @Test
    void getFlagConfig_whenWrongType_returnsEmpty() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(FeatureFlagCacheKeys.flagConfig(FLAG_NAME))).thenReturn("not-a-config");

        assertThat(cacheService.getFlagConfig(FLAG_NAME)).isEmpty();
    }

    @Test
    void getFlagConfig_whenHit_returnsConfig() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        CachedFlagConfig config = new CachedFlagConfig(FLAG_ID, FLAG_NAME, false, 3L);
        when(valueOperations.get(FeatureFlagCacheKeys.flagConfig(FLAG_NAME))).thenReturn(config);

        assertThat(cacheService.getFlagConfig(FLAG_NAME)).contains(config);
    }

    @Test
    void withoutRedisTemplate_allOperationsAreNoOps() {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(null);
        FeatureFlagCacheService noRedis = new FeatureFlagCacheService(redisTemplateProvider);

        assertThat(noRedis.getFlagConfig(FLAG_NAME)).isEmpty();
        noRedis.putFlagConfig(new CachedFlagConfig(FLAG_ID, FLAG_NAME, true, 1L));
        noRedis.evictFlagConfig(FLAG_NAME);
        assertThat(noRedis.getEvaluation(FLAG_ID, 1L, USER_ID)).isEmpty();
        noRedis.putEvaluation(FLAG_ID, 1L, USER_ID, new CachedEvaluation(true, "GLOBAL"));

        verifyNoInteractions(redisTemplate);
    }
}
