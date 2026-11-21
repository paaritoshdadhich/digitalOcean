package com.digitalocean.flagmanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.digitalocean.flagmanagement.controller.FeatureFlagController;
import com.digitalocean.flagmanagement.entity.FeatureFlag;
import com.digitalocean.flagmanagement.repository.AuditLogRepository;
import com.digitalocean.flagmanagement.repository.FeatureFlagOverrideRepository;
import com.digitalocean.flagmanagement.repository.FeatureFlagRepository;
import com.digitalocean.flagmanagement.support.AbstractIntegrationTest;

/**
 * Single HTTP end-to-end integration test that exercises the complete product flow
 * against real PostgreSQL and Redis (Testcontainers):
 * <p>
 * create → evaluate → global update → evaluate → user override → multi-user evaluate
 * → delete override → fallback evaluate → list → delete flag.
 * <p>
 * Use this test to demonstrate full-stack behavior to stakeholders.
 */
class FeatureFlagEndToEndFlowIT extends AbstractIntegrationTest {

    private static final String ACTOR = "leadership-demo";
    private static final String FLAG = "new_checkout";
    private static final String USER_ALICE = "user-alice";
    private static final String USER_BOB = "user-bob";

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
    @DisplayName("E2E HTTP flow: flag CRUD + evaluate + override + audit + cache-backed re-eval")
    void completeLeadershipDemoFlow_viaHttpLayer() throws Exception {
        // 1. Create flag (global OFF)
        mockMvc.perform(post("/api/v1/flags")
                        .header(FeatureFlagController.ACTOR_HEADER, ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "Leadership demo flag",
                                  "enabled": false
                                }
                                """.formatted(FLAG)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(FLAG))
                .andExpect(jsonPath("$.enabled").value(false));

        FeatureFlag created = featureFlagRepository.findByName(FLAG).orElseThrow();
        assertThat(auditLogRepository.findByFlagIdOrderByCreatedAtDesc(created.getId()))
                .anySatisfy(entry -> {
                    assertThat(entry.getActor()).isEqualTo(ACTOR);
                    assertThat(entry.getAction()).isEqualTo("CREATE");
                });

        // 2. Evaluate Alice → GLOBAL false
        evaluate(FLAG, USER_ALICE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.reason").value("GLOBAL"));

        // 3. Enable globally
        mockMvc.perform(put("/api/v1/flags/" + FLAG)
                        .header(FeatureFlagController.ACTOR_HEADER, ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Leadership demo flag",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        // 4. Re-evaluate Alice → GLOBAL true (cache invalidated after commit)
        evaluate(FLAG, USER_ALICE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("GLOBAL"));

        // 5. Override Bob to false
        mockMvc.perform(put("/api/v1/flags/" + FLAG + "/overrides/" + USER_BOB)
                        .header(FeatureFlagController.ACTOR_HEADER, ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "enabled": false }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flagName").value(FLAG))
                .andExpect(jsonPath("$.userId").value(USER_BOB))
                .andExpect(jsonPath("$.enabled").value(false));

        // 6. Multi-user evaluation
        evaluate(FLAG, USER_BOB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.reason").value("USER_OVERRIDE"));

        evaluate(FLAG, USER_ALICE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("GLOBAL"));

        // 7. Delete Bob's override → fallback to global
        mockMvc.perform(delete("/api/v1/flags/" + FLAG + "/overrides/" + USER_BOB)
                        .header(FeatureFlagController.ACTOR_HEADER, ACTOR))
                .andExpect(status().isNoContent());

        evaluate(FLAG, USER_BOB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.reason").value("GLOBAL"));

        // 8. List shows the flag
        mockMvc.perform(get("/api/v1/flags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value(FLAG));

        // 9. Get by name
        mockMvc.perform(get("/api/v1/flags/" + FLAG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        // 10. Delete flag → evaluate returns 404; audit history retained
        mockMvc.perform(delete("/api/v1/flags/" + FLAG)
                        .header(FeatureFlagController.ACTOR_HEADER, ACTOR))
                .andExpect(status().isNoContent());

        evaluate(FLAG, USER_ALICE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Feature flag not found: " + FLAG));

        assertThat(overrideRepository.findAll()).isEmpty();
        assertThat(featureFlagRepository.findByName(FLAG)).isEmpty();
        assertThat(auditLogRepository.findByFlagIdOrderByCreatedAtDesc(created.getId()))
                .extracting(entry -> entry.getAction())
                .contains("CREATE", "UPDATE", "DELETE");
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
}
