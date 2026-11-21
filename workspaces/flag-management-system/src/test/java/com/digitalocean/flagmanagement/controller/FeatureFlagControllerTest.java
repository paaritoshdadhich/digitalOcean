package com.digitalocean.flagmanagement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

class FeatureFlagControllerTest extends AbstractIntegrationTest {

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
    void createFlag_returnsCreated() throws Exception {
        mockMvc.perform(post("/api/v1/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "dark_mode",
                                  "description": "Enable dark mode UI",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("dark_mode"))
                .andExpect(jsonPath("$.description").value("Enable dark mode UI"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void createFlag_withoutActorHeader_defaultsActorToAnonymous() throws Exception {
        mockMvc.perform(post("/api/v1/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "anon_flag",
                                  "description": "no actor",
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isCreated());

        var flag = featureFlagRepository.findByName("anon_flag").orElseThrow();
        var audits = auditLogRepository.findByFlagIdOrderByCreatedAtDesc(flag.getId());
        assertThat(audits).hasSize(1);
        assertThat(audits.getFirst().getActor()).isEqualTo("anonymous");
    }

    @Test
    void createFlag_withNullDescription_returnsCreated() throws Exception {
        mockMvc.perform(post("/api/v1/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "no_desc",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("no_desc"))
                .andExpect(jsonPath("$.description").value(nullValue()));
    }

    @Test
    void createFlag_duplicateName_returnsConflict() throws Exception {
        createFlag("dark_mode", "Enable dark mode UI", true);

        mockMvc.perform(post("/api/v1/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "dark_mode",
                                  "description": "Duplicate",
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Feature flag already exists: dark_mode"))
                .andExpect(jsonPath("$.path").value("/api/v1/flags"));
    }

    @Test
    void createFlag_blankName_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "  ",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/v1/flags"));
    }

    @Test
    void createFlag_invalidNamePattern_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "1bad",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "name: must match ^[a-zA-Z][a-zA-Z0-9_-]{0,99}$"
                ));
    }

    @Test
    void createFlag_nameWithSpace_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "has space",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createFlag_missingEnabled_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "missing_enabled",
                                  "description": "oops"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("enabled: must not be null"));
    }

    @Test
    void createFlag_descriptionTooLong_returnsBadRequest() throws Exception {
        String tooLong = "x".repeat(501);
        mockMvc.perform(post("/api/v1/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "long_desc",
                                  "description": "%s",
                                  "enabled": true
                                }
                                """.formatted(tooLong)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createFlag_maxValidNameAndDescription_returnsCreated() throws Exception {
        String name = "A" + "a".repeat(99);
        String description = "d".repeat(500);

        mockMvc.perform(post("/api/v1/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "%s",
                                  "enabled": false
                                }
                                """.formatted(name, description)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.description").value(description));
    }

    @Test
    void getFlag_returnsOk() throws Exception {
        createFlag("new_dashboard", "Roll out new dashboard", false);

        mockMvc.perform(get("/api/v1/flags/new_dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("new_dashboard"))
                .andExpect(jsonPath("$.description").value("Roll out new dashboard"))
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void getFlag_missing_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/flags/does_not_exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Feature flag not found: does_not_exist"))
                .andExpect(jsonPath("$.path").value("/api/v1/flags/does_not_exist"));
    }

    @Test
    void updateFlag_returnsOk() throws Exception {
        createFlag("beta_banner", "Show beta banner", false);

        mockMvc.perform(put("/api/v1/flags/beta_banner")
                        .header(FeatureFlagController.ACTOR_HEADER, "admin-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "Updated beta banner copy",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("beta_banner"))
                .andExpect(jsonPath("$.description").value("Updated beta banner copy"))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void updateFlag_nullDescription_clearsDescription() throws Exception {
        createFlag("clear_desc", "will clear", true);

        mockMvc.perform(put("/api/v1/flags/clear_desc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": null,
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value(nullValue()));
    }

    @Test
    void updateFlag_missingEnabled_returnsBadRequest() throws Exception {
        createFlag("needs_enabled", "desc", true);

        mockMvc.perform(put("/api/v1/flags/needs_enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "only desc"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateFlag_missing_returnsNotFound() throws Exception {
        mockMvc.perform(put("/api/v1/flags/missing_flag")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "description": "x",
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Feature flag not found: missing_flag"));
    }

    @Test
    void deleteFlag_returnsNoContent() throws Exception {
        createFlag("legacy_widget", "Remove legacy widget", true);

        mockMvc.perform(delete("/api/v1/flags/legacy_widget"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/flags/legacy_widget"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteFlag_missing_returnsNotFound() throws Exception {
        mockMvc.perform(delete("/api/v1/flags/gone"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Feature flag not found: gone"));
    }

    @Test
    void deleteFlag_cascadesOverrides() throws Exception {
        createFlag("cascade_flag", "with overrides", true);

        mockMvc.perform(put("/api/v1/flags/cascade_flag/overrides/user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "enabled": false }
                                """))
                .andExpect(status().isOk());

        assertThat(overrideRepository.findAll()).hasSize(1);

        mockMvc.perform(delete("/api/v1/flags/cascade_flag"))
                .andExpect(status().isNoContent());

        assertThat(featureFlagRepository.findByName("cascade_flag")).isEmpty();
        assertThat(overrideRepository.findAll()).isEmpty();
    }

    @Test
    void listFlags_returnsAllFlags() throws Exception {
        createFlag("flag_a", "A", true);
        createFlag("flag_b", "B", false);

        mockMvc.perform(get("/api/v1/flags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void listFlags_empty_returnsEmptyArray() throws Exception {
        mockMvc.perform(get("/api/v1/flags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    private void createFlag(String name, String description, boolean enabled) throws Exception {
        mockMvc.perform(post("/api/v1/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "description": "%s",
                                  "enabled": %s
                                }
                                """.formatted(name, description, enabled)))
                .andExpect(status().isCreated());
    }
}
