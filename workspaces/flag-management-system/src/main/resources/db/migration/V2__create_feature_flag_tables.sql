-- Feature flags (source of truth for global flag state)
CREATE TABLE feature_flags (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    enabled     BOOLEAN NOT NULL DEFAULT FALSE,
    version     BIGINT NOT NULL DEFAULT 1,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_feature_flags_name ON feature_flags (name);

-- Per-user overrides.
-- Absence of a row means "no override" and is distinct from enabled = FALSE.
CREATE TABLE feature_flag_overrides (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flag_id    UUID NOT NULL,
    user_id    VARCHAR(100) NOT NULL,
    enabled    BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_feature_flag_overrides_flag
        FOREIGN KEY (flag_id) REFERENCES feature_flags (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_feature_flag_overrides_flag_user
    ON feature_flag_overrides (flag_id, user_id);

-- Immutable-style audit trail (no FK so history survives flag deletion)
CREATE TABLE audit_logs (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flag_id    UUID,
    actor      VARCHAR(100) NOT NULL,
    action     VARCHAR(30) NOT NULL,
    old_value  JSONB,
    new_value  JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_flag_created_at
    ON audit_logs (flag_id, created_at);
