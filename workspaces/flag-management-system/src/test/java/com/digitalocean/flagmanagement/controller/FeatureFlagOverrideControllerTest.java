package com.digitalocean.flagmanagement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.digitalocean.flagmanagement.repository.AuditLogRepository;
import com.digitalocean.flagmanagement.repository.FeatureFlagOverrideRepository;
import com.digitalocean.flagmanagement.repository.FeatureFlagRepository;
import com.digitalocean.flagmanagement.support.AbstractIntegrationTest;

class FeatureFlagOverrideControllerTest extends AbstractIntegrationTest {

    private static final String FLAG = "override_flag";
    private static final String USER = "user-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FeatureFlagRepository featureFlagRepository;

    @Autowired
    private FeatureFlagOverrideRepository overrideRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void cleanDatabase() {
        overrideRepository.deleteAll();
        featureFlagRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    @Test
    void upsertOverride_createsWhenMissing() throws Exception {
        createFlag(FLAG, false);

        mockMvc.perform(put("/api/v1/flags/" + FLAG + "/overrides/" + USER)
                        .header(FeatureFlagController.ACTOR_HEADER, "ops-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "enabled": true }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flagName").value(FLAG))
                .andExpect(jsonPath("$.userId").value(USER))
                .andExpect(jsonPath("$.enabled").value(true));

        assertThat(overrideRepository.findAll()).hasSize(1);
        var flag = featureFlagRepository.findByName(FLAG).orElseThrow();
        assertThat(flag.getVersion()).isGreaterThan(1L);
    }

    @Test
    void upsertOverride_updatesExistingWithoutDuplicateRow() throws Exception {
        createFlag(FLAG, true);

        upsert(USER, false);
        long versionAfterCreate = featureFlagRepository.findByName(FLAG).orElseThrow().getVersion();

        mockMvc.perform(put("/api/v1/flags/" + FLAG + "/overrides/" + USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "enabled": true }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        assertThat(overrideRepository.findAll()).hasSize(1);
        assertThat(featureFlagRepository.findByName(FLAG).orElseThrow().getVersion())
                .isGreaterThan(versionAfterCreate);
    }

    @Test
    void upsertOverride_explicitFalse_persistsRow() throws Exception {
        createFlag(FLAG, true);

        mockMvc.perform(put("/api/v1/flags/" + FLAG + "/overrides/" + USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "enabled": false }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        var flag = featureFlagRepository.findByName(FLAG).orElseThrow();
        assertThat(overrideRepository.findByFlagIdAndUserId(flag.getId(), USER)).isPresent();
        assertThat(overrideRepository.findByFlagIdAndUserId(flag.getId(), USER).orElseThrow().isEnabled())
                .isFalse();
    }

    @Test
    void upsertOverride_unknownFlag_returnsNotFound() throws Exception {
        mockMvc.perform(put("/api/v1/flags/missing/overrides/" + USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "enabled": true }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Feature flag not found: missing"));
    }

    @Test
    void upsertOverride_missingEnabled_returnsBadRequest() throws Exception {
        createFlag(FLAG, true);

        mockMvc.perform(put("/api/v1/flags/" + FLAG + "/overrides/" + USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("enabled: must not be null"));
    }

    @Test
    void deleteOverride_returnsNoContent() throws Exception {
        createFlag(FLAG, false);
        upsert(USER, true);

        mockMvc.perform(delete("/api/v1/flags/" + FLAG + "/overrides/" + USER)
                        .header(FeatureFlagController.ACTOR_HEADER, "ops-1"))
                .andExpect(status().isNoContent());

        var flag = featureFlagRepository.findByName(FLAG).orElseThrow();
        assertThat(overrideRepository.findByFlagIdAndUserId(flag.getId(), USER)).isEmpty();
    }

    @Test
    void deleteOverride_missingOverride_returnsNotFound() throws Exception {
        createFlag(FLAG, true);

        mockMvc.perform(delete("/api/v1/flags/" + FLAG + "/overrides/no-such-user"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(
                        "Override not found for flag 'override_flag' and user 'no-such-user'"
                ));
    }

    @Test
    void deleteOverride_unknownFlag_returnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/v1/flags/missing/overrides/" + USER))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Feature flag not found: missing"));
    }

    @Test
    void upsertOverride_withoutActor_defaultsToAnonymousInAudit() throws Exception {
        createFlag(FLAG, false);

        mockMvc.perform(put("/api/v1/flags/" + FLAG + "/overrides/" + USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "enabled": true }
                                """))
                .andExpect(status().isOk());

        var flag = featureFlagRepository.findByName(FLAG).orElseThrow();
        var latest = auditLogRepository.findByFlagIdOrderByCreatedAtDesc(flag.getId()).getFirst();
        assertThat(latest.getActor()).isEqualTo("anonymous");
        assertThat(latest.getAction()).isEqualTo("CREATE");
    }

    private void createFlag(String name, boolean enabled) throws Exception {
        mockMvc.perform(post("/api/v1/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "seed",
                                  "enabled": %s
                                }
                                """.formatted(name, enabled)))
                .andExpect(status().isCreated());
    }

    private void upsert(String userId, boolean enabled) throws Exception {
        mockMvc.perform(put("/api/v1/flags/" + FLAG + "/overrides/" + userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "enabled": %s }
                                """.formatted(enabled)))
                .andExpect(status().isOk());
    }
}
