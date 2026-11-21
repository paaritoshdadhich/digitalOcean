package com.digitalocean.flagmanagement.controller;

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

/**
 * HTTP-level evaluation coverage including business cases 1–10 from the QA plan.
 */
class FeatureFlagEvaluationControllerTest extends AbstractIntegrationTest {

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
    void evaluate_globalEnabled_noOverride_returnsGlobalTrue() throws Exception {
        createFlag("case1", true);

        evaluate("case1", "user-a")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flagName").value("case1"))
                .andExpect(jsonPath("$.userId").value("user-a"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("GLOBAL"));
    }

    @Test
    void evaluate_globalDisabled_noOverride_returnsGlobalFalse() throws Exception {
        createFlag("case2", false);

        evaluate("case2", "user-a")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.reason").value("GLOBAL"));
    }

    @Test
    void evaluate_globalEnabled_userOverrideFalse_returnsOverrideFalse() throws Exception {
        createFlag("case3", true);
        upsertOverride("case3", "user-a", false);

        evaluate("case3", "user-a")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.reason").value("USER_OVERRIDE"));
    }

    @Test
    void evaluate_globalDisabled_userOverrideTrue_returnsOverrideTrue() throws Exception {
        createFlag("case4", false);
        upsertOverride("case4", "user-a", true);

        evaluate("case4", "user-a")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("USER_OVERRIDE"));
    }

    @Test
    void evaluate_missingFlag_returnsNotFound() throws Exception {
        evaluate("missing_flag", "user-a")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Feature flag not found: missing_flag"))
                .andExpect(jsonPath("$.path").value("/api/v1/evaluate"));
    }

    @Test
    void evaluate_missingOverride_fallsBackToGlobal_distinctFromExplicitFalse() throws Exception {
        createFlag("case6", true);

        evaluate("case6", "user-without-override")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("GLOBAL"));

        upsertOverride("case6", "user-with-false", false);

        evaluate("case6", "user-with-false")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.reason").value("USER_OVERRIDE"));
    }

    @Test
    void evaluate_multipleFlags_areIndependent() throws Exception {
        createFlag("flag_alpha", true);
        createFlag("flag_beta", false);

        evaluate("flag_alpha", "user-a")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("GLOBAL"));

        evaluate("flag_beta", "user-a")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.reason").value("GLOBAL"));
    }

    @Test
    void evaluate_multipleUsers_withDifferentOverrides() throws Exception {
        createFlag("shared", false);
        upsertOverride("shared", "user1", true);

        evaluate("shared", "user1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("USER_OVERRIDE"));

        evaluate("shared", "user2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.reason").value("GLOBAL"));
    }

    @Test
    void evaluate_afterGlobalUpdate_reflectsNewState() throws Exception {
        createFlag("case9", true);

        evaluate("case9", "user-a")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("GLOBAL"));

        mockMvc.perform(put("/api/v1/flags/case9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "flipped",
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk());

        evaluate("case9", "user-a")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.reason").value("GLOBAL"));
    }

    @Test
    void evaluate_afterOverrideDelete_fallsBackToGlobal() throws Exception {
        createFlag("case10", false);
        upsertOverride("case10", "user-a", true);

        evaluate("case10", "user-a")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("USER_OVERRIDE"));

        mockMvc.perform(delete("/api/v1/flags/case10/overrides/user-a"))
                .andExpect(status().isNoContent());

        evaluate("case10", "user-a")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.reason").value("GLOBAL"));
    }

    @Test
    void evaluate_blankFlagName_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "flagName": "  ",
                                  "userId": "user-a"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void evaluate_blankUserId_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "flagName": "any",
                                  "userId": ""
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void evaluate_missingFields_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void evaluate_doesNotCreateAuditEntries() throws Exception {
        createFlag("no_audit_eval", true);
        var flag = featureFlagRepository.findByName("no_audit_eval").orElseThrow();
        int auditsBefore = auditLogRepository.findByFlagIdOrderByCreatedAtDesc(flag.getId()).size();

        evaluate("no_audit_eval", "user-a").andExpect(status().isOk());

        assertThatAuditsUnchanged(flag.getId(), auditsBefore);
    }

    @Test
    void health_endpointIsReachable() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    private void assertThatAuditsUnchanged(java.util.UUID flagId, int expectedSize) {
        org.assertj.core.api.Assertions.assertThat(
                auditLogRepository.findByFlagIdOrderByCreatedAtDesc(flagId)
        ).hasSize(expectedSize);
    }

    private org.springframework.test.web.servlet.ResultActions evaluate(String flagName, String userId)
            throws Exception {
        return mockMvc.perform(post("/api/v1/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "flagName": "%s",
                          "userId": "%s"
                        }
                        """.formatted(flagName, userId)));
    }

    private void createFlag(String name, boolean enabled) throws Exception {
        mockMvc.perform(post("/api/v1/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "eval seed",
                                  "enabled": %s
                                }
                                """.formatted(name, enabled)))
                .andExpect(status().isCreated());
    }

    private void upsertOverride(String flagName, String userId, boolean enabled) throws Exception {
        mockMvc.perform(put("/api/v1/flags/" + flagName + "/overrides/" + userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "enabled": %s }
                                """.formatted(enabled)))
                .andExpect(status().isOk());
    }
}
