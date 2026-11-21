package com.digitalocean.flagmanagement.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.digitalocean.flagmanagement.dto.EvaluateRequest;
import com.digitalocean.flagmanagement.entity.FeatureFlag;
import com.digitalocean.flagmanagement.repository.AuditLogRepository;
import com.digitalocean.flagmanagement.repository.FeatureFlagOverrideRepository;
import com.digitalocean.flagmanagement.repository.FeatureFlagRepository;
import com.digitalocean.flagmanagement.service.FeatureFlagEvaluationService;
import com.digitalocean.flagmanagement.support.AbstractIntegrationTest;

class FeatureFlagCacheInvalidationTest extends AbstractIntegrationTest {

    private static final String FLAG_NAME = "cache_demo";
    private static final String USER_ID = "user-cache";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FeatureFlagRepository featureFlagRepository;

    @Autowired
    private FeatureFlagOverrideRepository overrideRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private FeatureFlagEvaluationService evaluationService;

    @Autowired
    private AfterCommitCacheInvalidator cacheInvalidator;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void cleanState() {
        overrideRepository.deleteAll();
        featureFlagRepository.deleteAll();
        auditLogRepository.deleteAll();
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    @Test
    void cacheMiss_populatesFlagAndEvalKeys() {
        createFlagViaRepo(FLAG_NAME, true);

        assertThat(redisTemplate.hasKey(FeatureFlagCacheKeys.flagConfig(FLAG_NAME))).isFalse();

        evaluationService.evaluate(new EvaluateRequest(FLAG_NAME, USER_ID));

        FeatureFlag flag = featureFlagRepository.findByName(FLAG_NAME).orElseThrow();
        assertThat(redisTemplate.hasKey(FeatureFlagCacheKeys.flagConfig(FLAG_NAME))).isTrue();
        assertThat(redisTemplate.hasKey(
                FeatureFlagCacheKeys.evaluation(flag.getId(), flag.getVersion(), USER_ID)
        )).isTrue();
    }

    @Test
    void cacheHit_reusesCachedEvaluation() {
        createFlagViaRepo(FLAG_NAME, true);
        evaluationService.evaluate(new EvaluateRequest(FLAG_NAME, USER_ID));

        FeatureFlag flag = featureFlagRepository.findByName(FLAG_NAME).orElseThrow();
        String evalKey = FeatureFlagCacheKeys.evaluation(flag.getId(), flag.getVersion(), USER_ID);
        Long ttlBefore = redisTemplate.getExpire(evalKey, TimeUnit.SECONDS);

        var second = evaluationService.evaluate(new EvaluateRequest(FLAG_NAME, USER_ID));

        assertThat(second.enabled()).isTrue();
        assertThat(second.reason()).isEqualTo("GLOBAL");
        assertThat(redisTemplate.hasKey(evalKey)).isTrue();
        assertThat(ttlBefore).isPositive();
    }

    @Test
    void cachedKeys_haveApproximatelySixtySecondTtl() {
        createFlagViaRepo(FLAG_NAME, true);
        evaluationService.evaluate(new EvaluateRequest(FLAG_NAME, USER_ID));

        FeatureFlag flag = featureFlagRepository.findByName(FLAG_NAME).orElseThrow();
        Long flagTtl = redisTemplate.getExpire(FeatureFlagCacheKeys.flagConfig(FLAG_NAME), TimeUnit.SECONDS);
        Long evalTtl = redisTemplate.getExpire(
                FeatureFlagCacheKeys.evaluation(flag.getId(), flag.getVersion(), USER_ID),
                TimeUnit.SECONDS
        );

        assertThat(flagTtl).isBetween(50L, 60L);
        assertThat(evalTtl).isBetween(50L, 60L);
    }

    @Test
    void globalUpdate_incrementsVersionAndEvictsFlagConfigCache() throws Exception {
        createFlag(FLAG_NAME, true);
        evaluationService.evaluate(new EvaluateRequest(FLAG_NAME, USER_ID));

        assertThat(redisTemplate.hasKey(FeatureFlagCacheKeys.flagConfig(FLAG_NAME))).isTrue();
        long versionBefore = featureFlagRepository.findByName(FLAG_NAME).orElseThrow().getVersion();
        UUID flagId = featureFlagRepository.findByName(FLAG_NAME).orElseThrow().getId();
        assertThat(redisTemplate.hasKey(
                FeatureFlagCacheKeys.evaluation(flagId, versionBefore, USER_ID)
        )).isTrue();

        mockMvc.perform(put("/api/v1/flags/" + FLAG_NAME)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "updated",
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk());

        FeatureFlag updated = featureFlagRepository.findByName(FLAG_NAME).orElseThrow();
        assertThat(updated.getVersion()).isGreaterThan(versionBefore);
        assertThat(redisTemplate.hasKey(FeatureFlagCacheKeys.flagConfig(FLAG_NAME))).isFalse();

        evaluationService.evaluate(new EvaluateRequest(FLAG_NAME, USER_ID));
        assertThat(redisTemplate.hasKey(
                FeatureFlagCacheKeys.evaluation(flagId, updated.getVersion(), USER_ID)
        )).isTrue();
        assertThat(redisTemplate.hasKey(
                FeatureFlagCacheKeys.evaluation(flagId, versionBefore, USER_ID)
        )).isTrue(); // old versioned key may remain until TTL; new version is used instead
    }

    @Test
    void overrideUpdate_incrementsVersionAndEvictsFlagConfigCache() throws Exception {
        createFlag(FLAG_NAME, false);
        evaluationService.evaluate(new EvaluateRequest(FLAG_NAME, USER_ID));

        assertThat(redisTemplate.hasKey(FeatureFlagCacheKeys.flagConfig(FLAG_NAME))).isTrue();
        long versionBefore = featureFlagRepository.findByName(FLAG_NAME).orElseThrow().getVersion();
        UUID flagId = featureFlagRepository.findByName(FLAG_NAME).orElseThrow().getId();

        mockMvc.perform(put("/api/v1/flags/" + FLAG_NAME + "/overrides/" + USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk());

        FeatureFlag updated = featureFlagRepository.findByName(FLAG_NAME).orElseThrow();
        assertThat(updated.getVersion()).isGreaterThan(versionBefore);
        assertThat(redisTemplate.hasKey(FeatureFlagCacheKeys.flagConfig(FLAG_NAME))).isFalse();

        var response = evaluationService.evaluate(new EvaluateRequest(FLAG_NAME, USER_ID));
        assertThat(response.enabled()).isTrue();
        assertThat(response.reason()).isEqualTo("USER_OVERRIDE");
        assertThat(redisTemplate.hasKey(
                FeatureFlagCacheKeys.evaluation(flagId, updated.getVersion(), USER_ID)
        )).isTrue();
    }

    @Test
    void overrideDelete_evictsFlagConfigAndFallsBackToGlobal() throws Exception {
        createFlag(FLAG_NAME, false);
        mockMvc.perform(put("/api/v1/flags/" + FLAG_NAME + "/overrides/" + USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "enabled": true }
                                """))
                .andExpect(status().isOk());

        evaluationService.evaluate(new EvaluateRequest(FLAG_NAME, USER_ID));
        assertThat(redisTemplate.hasKey(FeatureFlagCacheKeys.flagConfig(FLAG_NAME))).isTrue();

        mockMvc.perform(delete("/api/v1/flags/" + FLAG_NAME + "/overrides/" + USER_ID))
                .andExpect(status().isNoContent());

        assertThat(redisTemplate.hasKey(FeatureFlagCacheKeys.flagConfig(FLAG_NAME))).isFalse();

        var response = evaluationService.evaluate(new EvaluateRequest(FLAG_NAME, USER_ID));
        assertThat(response.enabled()).isFalse();
        assertThat(response.reason()).isEqualTo("GLOBAL");
    }

    @Test
    void flagDelete_evictsFlagConfigAndEvaluateReturnsNotFound() throws Exception {
        createFlag(FLAG_NAME, true);
        evaluationService.evaluate(new EvaluateRequest(FLAG_NAME, USER_ID));
        assertThat(redisTemplate.hasKey(FeatureFlagCacheKeys.flagConfig(FLAG_NAME))).isTrue();

        mockMvc.perform(delete("/api/v1/flags/" + FLAG_NAME))
                .andExpect(status().isNoContent());

        assertThat(redisTemplate.hasKey(FeatureFlagCacheKeys.flagConfig(FLAG_NAME))).isFalse();
        assertThat(featureFlagRepository.findByName(FLAG_NAME)).isEmpty();
    }

    @Test
    void rolledBackTransaction_doesNotEvictFlagConfigCache() {
        createFlagViaRepo(FLAG_NAME, true);
        evaluationService.evaluate(new EvaluateRequest(FLAG_NAME, USER_ID));
        assertThat(redisTemplate.hasKey(FeatureFlagCacheKeys.flagConfig(FLAG_NAME))).isTrue();

        try {
            transactionTemplate.executeWithoutResult(status -> {
                cacheInvalidator.invalidateFlagConfig(FLAG_NAME);
                throw new IllegalStateException("force rollback");
            });
        } catch (IllegalStateException ignored) {
            // expected
        }

        assertThat(redisTemplate.hasKey(FeatureFlagCacheKeys.flagConfig(FLAG_NAME))).isTrue();
    }

    private void createFlag(String name, boolean enabled) throws Exception {
        mockMvc.perform(post("/api/v1/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "cache test",
                                  "enabled": %s
                                }
                                """.formatted(name, enabled)))
                .andExpect(status().isCreated());
    }

    private void createFlagViaRepo(String name, boolean enabled) {
        featureFlagRepository.save(new FeatureFlag(name, "cache test", enabled));
    }
}
