# Feature Flag Management System

Production-oriented MVP foundation for a Feature Flag Management REST API.

## Stack

- Java 21
- Spring Boot 3.5
- Maven
- PostgreSQL + Flyway
- Redis (cache)
- Spring Data JPA
- Docker Compose
- JUnit 5, Mockito, Testcontainers

## Package layout

```
com.digitalocean.flagmanagement
├── controller
├── service
├── repository
├── entity
├── dto
├── cache
├── exception
└── config
```

## Prerequisites

- JDK 21
- Maven 3.9+
- Docker / Docker Compose

## Start infrastructure

From this directory:

```bash
docker compose up -d
```

Confirm healthy services:

```bash
docker compose ps
```

## Run the application

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn spring-boot:run
```

Health check:

```bash
curl http://localhost:8080/actuator/health
```

## Build and test

```bash
mvn clean verify
```

Tests use Testcontainers for PostgreSQL and Redis (Docker required).

## Configuration

Defaults in `src/main/resources/application.yml`:

| Setting | Default |
|---------|---------|
| Server port | `8080` |
| Postgres | `localhost:5433/feature_flags` (container port 5432) |
| Redis | `localhost:6379` |

Override with env vars: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `SERVER_PORT`.
