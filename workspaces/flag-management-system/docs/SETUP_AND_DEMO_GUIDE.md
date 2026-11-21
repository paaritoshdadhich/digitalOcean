# Setup, Demo & Operations Guide

Step-by-step instructions to run this project on a **fresh machine** after cloning/forking from GitHub, exercise every feature for a demo, call every HTTP endpoint with example bodies, and inspect PostgreSQL / Redis state.

Architecture deep dive: [ARCHITECTURE.md](./ARCHITECTURE.md).  
Short overview: [../README.md](../README.md).

---

## 1. Prerequisites (new machine checklist)

Install and verify:

| Tool | Version guidance | Check command |
|------|------------------|---------------|
| JDK | **21** (required by `pom.xml`) | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Docker | Engine running (OrbStack / Docker Desktop / Colima OK) | `docker info` |
| Docker Compose | v2 plugin | `docker compose version` |
| curl | any modern | `curl --version` |
| Optional: `psql` / `redis-cli` | for DB/cache inspection | see §7 |

```bash
java -version          # should show 21.x
mvn -version
docker info            # must connect to a daemon (not "permission denied")
docker compose version
```

If `java` is not 21, set `JAVA_HOME` to a JDK 21 install before building.

---

## 2. Get the code

```bash
git clone <YOUR_FORK_OR_REPO_URL>.git
cd digitalOcean/workspaces/flag-management-system
```

(If the monorepo layout differs, open the directory that contains this project's `pom.xml` and `docker-compose.yml`.)

---

## 3. Start infrastructure (Postgres + Redis)

```bash
docker compose up -d
docker compose ps
```

Expected:

| Service | Container name | Host port | Purpose |
|---------|----------------|-----------|---------|
| Postgres 16 | `flag-management-postgres` | **5433** → 5432 | Source of truth |
| Redis 7 | `flag-management-redis` | **6379** → 6379 | Evaluation cache |

Health:

```bash
docker compose ps
# Both should show healthy (or at least Up)

# Optional probes
docker exec flag-management-postgres pg_isready -U feature_flags -d feature_flags
docker exec flag-management-redis redis-cli ping   # PONG
```

**Why 5433?** Avoids clashing with a local Homebrew/system Postgres often bound to `5432`.

Stop infra (keeps volumes):

```bash
docker compose stop
```

Reset data volumes (destructive):

```bash
docker compose down -v
docker compose up -d
```

---

## 4. Build, test, and run the application

### 4.1 Run the automated test suite (recommended before demo)

Tests spin up **their own** Postgres/Redis via Testcontainers. Compose does **not** need to be up for tests, but Docker **must** be available.

```bash
mvn clean verify
```

Showcase end-to-end HTTP flow only:

```bash
mvn -Dtest=FeatureFlagEndToEndFlowIT test
```

### 4.2 Start the API

With Compose already running:

```bash
mvn spring-boot:run
```

Wait until logs show the app started, then:

```bash
curl -sf http://localhost:8080/actuator/health
# {"status":"UP"}
```

Optional env overrides:

```bash
export DB_HOST=localhost DB_PORT=5433
export DB_NAME=feature_flags DB_USER=feature_flags DB_PASSWORD=feature_flags
export REDIS_HOST=localhost REDIS_PORT=6379
export SERVER_PORT=8080
mvn spring-boot:run
```

### 4.3 Leadership demo script

```bash
chmod +x scripts/demo-e2e.sh
./scripts/demo-e2e.sh
```

Optional:

```bash
FLAG=my_demo_flag USER_A=alice USER_B=bob ACTOR=exec-demo ./scripts/demo-e2e.sh
```

Flow covered: health → create (global off) → evaluate → enable global → evaluate → override → multi-user evaluate → delete override → list.

---

## 5. Demo readiness checklist

Before showing leadership:

1. [ ] `docker compose ps` — Postgres + Redis healthy  
2. [ ] `mvn clean verify` — green (or at least `FeatureFlagEndToEndFlowIT`)  
3. [ ] `mvn spring-boot:run` — health `UP`  
4. [ ] `./scripts/demo-e2e.sh` — completes without errors  
5. [ ] Optional: open `psql` and show `feature_flags` / `audit_logs` after the script (§7)

If health is `DOWN` or Redis/DB fail: restart Compose, confirm ports `5433`/`6379` are free, check app logs.

---

## 6. Complete HTTP API reference (for manual testing)

**Base URL:** `http://localhost:8080`

**Common header (mutating admin APIs):**

```http
Content-Type: application/json
X-Actor-Id: admin-42
```

If `X-Actor-Id` is omitted, actor is stored as `anonymous`.

**Standard error body:**

```json
{
  "timestamp": "2026-11-21T06:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "enabled: must not be null",
  "path": "/api/v1/flags"
}
```

| Status | Meaning |
|--------|---------|
| `400` | Validation failure |
| `404` | Flag or override not found |
| `409` | Duplicate flag name |
| `500` | Unexpected server error |

---

### 6.1 Create flag — `POST /api/v1/flags` → `201`

**Headers:** `Content-Type`, optional `X-Actor-Id`  
**Path/query:** none  

**Body:**

```json
{
  "name": "new_checkout",
  "description": "New checkout flow",
  "enabled": false
}
```

| Field | Rules |
|-------|--------|
| `name` | Required; pattern `^[a-zA-Z][a-zA-Z0-9_-]{0,99}$` (max 100 chars) |
| `description` | Optional; max 500 |
| `enabled` | Required boolean |

```bash
curl -s -X POST http://localhost:8080/api/v1/flags \
  -H 'Content-Type: application/json' \
  -H 'X-Actor-Id: admin-42' \
  -d '{
    "name": "new_checkout",
    "description": "New checkout flow",
    "enabled": false
  }'
```

**Success response (`201`):**

```json
{
  "id": "…uuid…",
  "name": "new_checkout",
  "description": "New checkout flow",
  "enabled": false,
  "createdAt": "…",
  "updatedAt": "…"
}
```

---

### 6.2 List flags — `GET /api/v1/flags` → `200`

```bash
curl -s http://localhost:8080/api/v1/flags
```

Returns a JSON array of flag objects (may be `[]`).

---

### 6.3 Get flag by name — `GET /api/v1/flags/{name}` → `200` / `404`

```bash
curl -s http://localhost:8080/api/v1/flags/new_checkout
```

---

### 6.4 Update flag — `PUT /api/v1/flags/{name}` → `200` / `404`

**Body:**

```json
{
  "description": "New checkout flow",
  "enabled": true
}
```

`enabled` is required. `description` may be `null` (clears description).

```bash
curl -s -X PUT http://localhost:8080/api/v1/flags/new_checkout \
  -H 'Content-Type: application/json' \
  -H 'X-Actor-Id: admin-42' \
  -d '{
    "description": "New checkout flow",
    "enabled": true
  }'
```

---

### 6.5 Delete flag — `DELETE /api/v1/flags/{name}` → `204` / `404`

Cascades overrides in the database. Audit rows remain.

```bash
curl -s -o /dev/null -w "%{http_code}\n" \
  -X DELETE http://localhost:8080/api/v1/flags/new_checkout \
  -H 'X-Actor-Id: admin-42'
```

---

### 6.6 Upsert user override — `PUT /api/v1/flags/{name}/overrides/{userId}` → `200` / `404`

**Path params:** `name` (flag), `userId`  
**Body:**

```json
{ "enabled": true }
```

```bash
curl -s -X PUT http://localhost:8080/api/v1/flags/new_checkout/overrides/user123 \
  -H 'Content-Type: application/json' \
  -H 'X-Actor-Id: admin-42' \
  -d '{ "enabled": true }'
```

**Success:**

```json
{
  "flagName": "new_checkout",
  "userId": "user123",
  "enabled": true
}
```

There is **no** GET override API — confirm via evaluate or SQL (§7).

---

### 6.7 Delete user override — `DELETE /api/v1/flags/{name}/overrides/{userId}` → `204` / `404`

```bash
curl -s -o /dev/null -w "%{http_code}\n" \
  -X DELETE http://localhost:8080/api/v1/flags/new_checkout/overrides/user123 \
  -H 'X-Actor-Id: admin-42'
```

---

### 6.8 Evaluate — `POST /api/v1/evaluate` → `200` / `404`

**No** `X-Actor-Id` required.

**Body:**

```json
{
  "flagName": "new_checkout",
  "userId": "user123"
}
```

```bash
curl -s -X POST http://localhost:8080/api/v1/evaluate \
  -H 'Content-Type: application/json' \
  -d '{
    "flagName": "new_checkout",
    "userId": "user123"
  }'
```

**Success:**

```json
{
  "flagName": "new_checkout",
  "userId": "user123",
  "enabled": true,
  "reason": "USER_OVERRIDE"
}
```

`reason` is `USER_OVERRIDE` or `GLOBAL`.

**Precedence:** override row if present → else global `enabled` → else `404` if flag missing.

---

### 6.9 Health — `GET /actuator/health` → `200`

```bash
curl -s http://localhost:8080/actuator/health
```

---

### 6.10 Suggested manual demo sequence

```bash
# 1. Create OFF
curl -s -X POST http://localhost:8080/api/v1/flags \
  -H 'Content-Type: application/json' -H 'X-Actor-Id: demo' \
  -d '{"name":"demo_flag","description":"demo","enabled":false}'

# 2. Evaluate → GLOBAL false
curl -s -X POST http://localhost:8080/api/v1/evaluate \
  -H 'Content-Type: application/json' \
  -d '{"flagName":"demo_flag","userId":"alice"}'

# 3. Enable globally
curl -s -X PUT http://localhost:8080/api/v1/flags/demo_flag \
  -H 'Content-Type: application/json' -H 'X-Actor-Id: demo' \
  -d '{"description":"demo","enabled":true}'

# 4. Override bob OFF
curl -s -X PUT http://localhost:8080/api/v1/flags/demo_flag/overrides/bob \
  -H 'Content-Type: application/json' -H 'X-Actor-Id: demo' \
  -d '{"enabled":false}'

# 5. Compare users
curl -s -X POST http://localhost:8080/api/v1/evaluate -H 'Content-Type: application/json' \
  -d '{"flagName":"demo_flag","userId":"alice"}'
curl -s -X POST http://localhost:8080/api/v1/evaluate -H 'Content-Type: application/json' \
  -d '{"flagName":"demo_flag","userId":"bob"}'
```

---

## 7. Inspecting the database (PostgreSQL)

### 7.1 Connect with `psql` via Docker

```bash
docker exec -it flag-management-postgres \
  psql -U feature_flags -d feature_flags
```

Connection details (from `docker-compose.yml` / `application.yml`):

| Property | Value |
|----------|-------|
| Host (from host machine) | `localhost` |
| Port | `5433` |
| Database | `feature_flags` |
| User | `feature_flags` |
| Password | `feature_flags` |

From host with local `psql`:

```bash
PGPASSWORD=feature_flags psql -h localhost -p 5433 -U feature_flags -d feature_flags
```

### 7.2 List tables & schema

```sql
\dt
\d feature_flags
\d feature_flag_overrides
\d audit_logs
```

### 7.3 Indexes

```sql
SELECT tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public'
ORDER BY tablename, indexname;
```

Expected application indexes:

| Index | Table | Purpose |
|-------|-------|---------|
| `idx_feature_flags_name` | `feature_flags` | Unique flag name lookup |
| `idx_feature_flag_overrides_flag_user` | `feature_flag_overrides` | Unique `(flag_id, user_id)` |
| `idx_audit_logs_flag_created_at` | `audit_logs` | History by flag + time |
| Primary keys | all tables | UUID PKs |

Also inspect Flyway history:

```sql
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
```

### 7.4 Table data queries

**All flags:**

```sql
SELECT id, name, description, enabled, version, created_at, updated_at
FROM feature_flags
ORDER BY name;
```

**Overrides (join for readability):**

```sql
SELECT f.name AS flag_name, o.user_id, o.enabled, o.created_at, o.updated_at
FROM feature_flag_overrides o
JOIN feature_flags f ON f.id = o.flag_id
ORDER BY f.name, o.user_id;
```

**Audit trail (newest first):**

```sql
SELECT id, flag_id, actor, action, old_value, new_value, created_at
FROM audit_logs
ORDER BY created_at DESC
LIMIT 50;
```

**Pretty-print JSONB:**

```sql
SELECT actor, action,
       jsonb_pretty(old_value) AS old_value,
       jsonb_pretty(new_value) AS new_value,
       created_at
FROM audit_logs
ORDER BY created_at DESC
LIMIT 20;
```

**Audit for a specific flag name (including after delete):**

```sql
SELECT a.actor, a.action, a.old_value, a.new_value, a.created_at
FROM audit_logs a
WHERE a.flag_id = (
  SELECT id FROM feature_flags WHERE name = 'new_checkout'
  -- if flag already deleted, paste the UUID from an earlier SELECT
)
ORDER BY a.created_at DESC;
```

After a flag is deleted, use the UUID captured from create response or from prior audit rows:

```sql
SELECT * FROM audit_logs WHERE flag_id = 'PASTE-UUID-HERE' ORDER BY created_at DESC;
```

### 7.5 Constraints & row counts

```sql
SELECT
  (SELECT count(*) FROM feature_flags) AS flags,
  (SELECT count(*) FROM feature_flag_overrides) AS overrides,
  (SELECT count(*) FROM audit_logs) AS audits;

SELECT conname, contype, pg_get_constraintdef(oid)
FROM pg_constraint
WHERE conrelid IN (
  'feature_flags'::regclass,
  'feature_flag_overrides'::regclass,
  'audit_logs'::regclass
);
```

---

## 8. Inspecting Redis cache

```bash
docker exec -it flag-management-redis redis-cli
```

Useful commands:

```text
PING
KEYS flag:*
KEYS eval:*
GET flag:new_checkout
TTL flag:new_checkout
FLUSHDB          # demo only — clears cache; data reloads from Postgres on next evaluate
```

Key shapes:

| Key | Meaning |
|-----|---------|
| `flag:{flagName}` | Cached flag id/name/enabled/version |
| `eval:{flagId}:{version}:{userId}` | Cached evaluation result |

TTL is **60 seconds**. After a successful flag/override write, `flag:{name}` is deleted; old `eval:*` keys for prior versions may linger until TTL.

---

## 9. Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| `Connection refused` to `5433` / `6379` | Compose not running | `docker compose up -d` |
| App health DOWN | DB/Redis unreachable or wrong env | Check `application.yml` ports; `docker compose ps` |
| Tests hang / connection refused on random high ports | Docker daemon issue or old Testcontainers leftovers | Ensure `docker info` works; prune stopped test containers |
| `409` on create | Flag name already exists | Use a new name or delete existing |
| `404` on evaluate | Flag never created / already deleted | Create flag first |
| Port `8080` in use | Another process bound | `SERVER_PORT=8081 mvn spring-boot:run` |
| Wrong Java version | JDK ≠ 21 | Install JDK 21 and set `JAVA_HOME` |

---

## 10. What “done” looks like on a fresh fork

1. Clone → `cd workspaces/flag-management-system`  
2. `docker compose up -d`  
3. `mvn clean verify` → green  
4. `mvn spring-boot:run` → `/actuator/health` is `UP`  
5. `./scripts/demo-e2e.sh` → full feature walkthrough succeeds  
6. Optional: `psql` shows rows in `feature_flags` / `audit_logs`; `redis-cli` shows `flag:*` / `eval:*` after evaluate  

You are then ready to demonstrate all features without relying on any machine-specific state beyond Docker + JDK 21 + Maven.
