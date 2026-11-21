package com.digitalocean.flagmanagement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.digitalocean.flagmanagement.controller.FeatureFlagController;
import com.digitalocean.flagmanagement.entity.AuditLog;
import com.digitalocean.flagmanagement.entity.FeatureFlag;
import com.digitalocean.flagmanagement.repository.AuditLogRepository;
import com.digitalocean.flagmanagement.repository.FeatureFlagOverrideRepository;
import com.digitalocean.flagmanagement.repository.FeatureFlagRepository;
import com.digitalocean.flagmanagement.support.AbstractIntegrationTest;

class AuditLogIntegrationTest extends AbstractIntegrationTest {

    private static final String ACTOR = "admin-42";
    private static final String FLAG_NAME = "audit_flag";
    private static final String USER_ID = "user-9";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FeatureFlagRepository featureFlagRepository;

    @Autowired
    private FeatureFlagOverrideRepository overrideRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void cleanState() {
        overrideRepository.deleteAll();
        featureFlagRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    @Test
    void createUpdateDeleteFlag_writesAuditEntriesWithActorAndValues() throws Exception {
        mockMvc.perform(post("/api/v1/flags")
                        .header(FeatureFlagController.ACTOR_HEADER, ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "initial",
                                  "enabled": false
                                }
                                """.formatted(FLAG_NAME)))
                .andExpect(status().isCreated());

        FeatureFlag flag = featureFlagRepository.findByName(FLAG_NAME).orElseThrow();
        List<AuditLog> afterCreate = auditLogRepository.findByFlagIdOrderByCreatedAtDesc(flag.getId());
        assertThat(afterCreate).hasSize(1);
        assertAudit(afterCreate.getFirst(), ACTOR, "CREATE", null, Map.of(
                "name", FLAG_NAME,
                "description", "initial",
                "enabled", false
        ));

        mockMvc.perform(put("/api/v1/flags/" + FLAG_NAME)
                        .header(FeatureFlagController.ACTOR_HEADER, ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "updated",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk());

        List<AuditLog> afterUpdate = auditLogRepository.findByFlagIdOrderByCreatedAtDesc(flag.getId());
        assertThat(afterUpdate).hasSize(2);
        assertAudit(afterUpdate.getFirst(), ACTOR, "UPDATE", Map.of(
                "name", FLAG_NAME,
                "description", "initial",
                "enabled", false
        ), Map.of(
                "name", FLAG_NAME,
                "description", "updated",
                "enabled", true
        ));

        mockMvc.perform(delete("/api/v1/flags/" + FLAG_NAME)
                        .header(FeatureFlagController.ACTOR_HEADER, ACTOR))
                .andExpect(status().isNoContent());

        List<AuditLog> afterDelete = auditLogRepository.findByFlagIdOrderByCreatedAtDesc(flag.getId());
        assertThat(afterDelete).hasSize(3);
        assertAudit(afterDelete.getFirst(), ACTOR, "DELETE", Map.of(
                "name", FLAG_NAME,
                "description", "updated",
                "enabled", true
        ), null);
        assertThat(featureFlagRepository.findByName(FLAG_NAME)).isEmpty();
    }

    @Test
    void overrideCreateUpdateDelete_writesAuditEntries() throws Exception {
        createFlag(FLAG_NAME, false);
        FeatureFlag flag = featureFlagRepository.findByName(FLAG_NAME).orElseThrow();

        mockMvc.perform(put("/api/v1/flags/" + FLAG_NAME + "/overrides/" + USER_ID)
                        .header(FeatureFlagController.ACTOR_HEADER, ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "enabled": true }
                                """))
                .andExpect(status().isOk());

        List<AuditLog> afterCreate = auditLogRepository.findByFlagIdOrderByCreatedAtDesc(flag.getId());
        assertThat(afterCreate.getFirst().getAction()).isEqualTo("CREATE");
        assertThat(afterCreate.getFirst().getActor()).isEqualTo(ACTOR);
        assertThat(afterCreate.getFirst().getOldValue()).isNull();
        assertThat(afterCreate.getFirst().getNewValue()).isEqualTo(Map.of(
                "userId", USER_ID,
                "enabled", true
        ));

        mockMvc.perform(put("/api/v1/flags/" + FLAG_NAME + "/overrides/" + USER_ID)
                        .header(FeatureFlagController.ACTOR_HEADER, ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "enabled": false }
                                """))
                .andExpect(status().isOk());

        List<AuditLog> afterUpdate = auditLogRepository.findByFlagIdOrderByCreatedAtDesc(flag.getId());
        assertThat(afterUpdate.getFirst().getAction()).isEqualTo("UPDATE");
        assertThat(afterUpdate.getFirst().getOldValue()).isEqualTo(Map.of(
                "userId", USER_ID,
                "enabled", true
        ));
        assertThat(afterUpdate.getFirst().getNewValue()).isEqualTo(Map.of(
                "userId", USER_ID,
                "enabled", false
        ));

        mockMvc.perform(delete("/api/v1/flags/" + FLAG_NAME + "/overrides/" + USER_ID)
                        .header(FeatureFlagController.ACTOR_HEADER, ACTOR))
                .andExpect(status().isNoContent());

        List<AuditLog> afterDelete = auditLogRepository.findByFlagIdOrderByCreatedAtDesc(flag.getId());
        assertThat(afterDelete.getFirst().getAction()).isEqualTo("DELETE");
        assertThat(afterDelete.getFirst().getOldValue()).isEqualTo(Map.of(
                "userId", USER_ID,
                "enabled", false
        ));
        assertThat(afterDelete.getFirst().getNewValue()).isNull();
    }

    @Test
    void auditRecordIsRolledBackWhenTransactionFails() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            FeatureFlag flag = featureFlagRepository.save(
                    new FeatureFlag("rollback_flag", "should roll back", true)
            );
            auditLogService.record(
                    flag.getId(),
                    ACTOR,
                    AuditAction.CREATE,
                    null,
                    AuditLogService.flagValue(flag)
            );
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("force rollback");

        assertThat(featureFlagRepository.findByName("rollback_flag")).isEmpty();
        assertThat(auditLogRepository.findAll()).isEmpty();
    }

    @Test
    void mutatingWithoutActorHeader_recordsAnonymous() throws Exception {
        mockMvc.perform(post("/api/v1/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "anon_audit",
                                  "description": "seed",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isCreated());

        FeatureFlag flag = featureFlagRepository.findByName("anon_audit").orElseThrow();
        assertThat(auditLogRepository.findByFlagIdOrderByCreatedAtDesc(flag.getId()).getFirst().getActor())
                .isEqualTo("anonymous");
    }

    @Test
    void failedValidation_doesNotWriteAudit() throws Exception {
        long before = auditLogRepository.count();

        mockMvc.perform(post("/api/v1/flags")
                        .header(FeatureFlagController.ACTOR_HEADER, ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "1invalid",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertThat(auditLogRepository.count()).isEqualTo(before);
    }

    @Test
    void notFoundUpdate_doesNotWriteAudit() throws Exception {
        long before = auditLogRepository.count();

        mockMvc.perform(put("/api/v1/flags/does_not_exist")
                        .header(FeatureFlagController.ACTOR_HEADER, ACTOR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "x",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isNotFound());

        assertThat(auditLogRepository.count()).isEqualTo(before);
    }

    @Test
    void auditEntriesSurviveFlagDeletion() throws Exception {
        createFlag("survive_delete", true);
        FeatureFlag flag = featureFlagRepository.findByName("survive_delete").orElseThrow();
        UUID flagId = flag.getId();

        mockMvc.perform(delete("/api/v1/flags/survive_delete")
                        .header(FeatureFlagController.ACTOR_HEADER, ACTOR))
                .andExpect(status().isNoContent());

        List<AuditLog> remaining = auditLogRepository.findByFlagIdOrderByCreatedAtDesc(flagId);
        assertThat(remaining).hasSize(2); // CREATE + DELETE
        assertThat(remaining.stream().map(AuditLog::getAction).toList())
                .containsExactly("DELETE", "CREATE");
        assertThat(featureFlagRepository.findByName("survive_delete")).isEmpty();
    }

    private void createFlag(String name, boolean enabled) throws Exception {
        mockMvc.perform(post("/api/v1/flags")
                        .header(FeatureFlagController.ACTOR_HEADER, ACTOR)
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

    private static void assertAudit(
            AuditLog entry,
            String actor,
            String action,
            Map<String, Object> oldValue,
            Map<String, Object> newValue
    ) {
        assertThat(entry.getActor()).isEqualTo(actor);
        assertThat(entry.getAction()).isEqualTo(action);
        assertThat(entry.getFlagId()).isNotNull();
        assertThat(entry.getCreatedAt()).isNotNull();
        assertThat(entry.getOldValue()).isEqualTo(oldValue);
        assertThat(entry.getNewValue()).isEqualTo(newValue);
    }
}
