package com.digitalocean.flagmanagement.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.digitalocean.flagmanagement.repository.FeatureFlagRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class FeatureFlagControllerTest {

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

    @BeforeEach
    void cleanDatabase() {
        featureFlagRepository.deleteAll();
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
                .andExpect(jsonPath("$.id").exists());
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
                .andExpect(jsonPath("$.message").value("Feature flag already exists: dark_mode"));
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
    void updateFlag_returnsOk() throws Exception {
        createFlag("beta_banner", "Show beta banner", false);

        mockMvc.perform(put("/api/v1/flags/beta_banner")
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
    void deleteFlag_returnsNoContent() throws Exception {
        createFlag("legacy_widget", "Remove legacy widget", true);

        mockMvc.perform(delete("/api/v1/flags/legacy_widget"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/flags/legacy_widget"))
                .andExpect(status().isNotFound());
    }

    @Test
    void listFlags_returnsAllFlags() throws Exception {
        createFlag("flag_a", "A", true);
        createFlag("flag_b", "B", false);

        mockMvc.perform(get("/api/v1/flags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
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
