package com.digitalocean.flagmanagement.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Spring Boot + Testcontainers base for integration tests.
 * <p>
 * Containers are JVM singletons: started once in a static initializer and <b>not</b>
 * annotated with {@code @Container}. That avoids the failure mode where JUnit stops
 * containers after the first test class while Spring Boot reuses a cached application
 * context still pointed at the old mapped ports (connection refused on Postgres/Redis).
 * <p>
 * Ryuk still reaps these containers when the test JVM exits.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:16-alpine");
    private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7-alpine");

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE);

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>(REDIS_IMAGE)
            .withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }
}
