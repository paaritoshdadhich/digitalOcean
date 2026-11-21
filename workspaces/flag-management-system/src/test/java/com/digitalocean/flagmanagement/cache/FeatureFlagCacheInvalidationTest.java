package com.digitalocean.flagmanagement.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.digitalocean.flagmanagement.entity.FeatureFlag;
import com.digitalocean.flagmanagement.repository.FeatureFlagOverrideRepository;
import com.digitalocean.flagmanagement.repository.FeatureFlagRepository;
import com.digitalocean.flagmanagement.service.FeatureFlagEvaluationService;
import com.digitalocean.flagmanagement.dto.EvaluateRequest;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FeatureFlagCacheInvalidationTest {

    private static final String FLAG_NAME = "cache_demo";
    private static final String USER_ID = "user-cache";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine")
    );

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FeatureFlagRepository featureFlagRepository;

    @Autowired
    private FeatureFlagOverrideRepository overrideRepository;

    @Autowired
    private FeatureFlagEvaluationService evaluationService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void cleanState() {
        overrideRepository.deleteAll();
        featureFlagRepository.deleteAll();
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
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
}
