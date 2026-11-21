package com.digitalocean.flagmanagement.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Explicit per-user override for a feature flag.
 * <p>
 * Missing row = no override. A persisted row with {@code enabled=false} is an
 * explicit disable and must not be confused with the absence of an override.
 */
@Entity
@Table(
        name = "feature_flag_overrides",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_feature_flag_overrides_flag_user",
                columnNames = {"flag_id", "user_id"}
        )
)
@EntityListeners(AuditingEntityListener.class)
public class FeatureFlagOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "flag_id", nullable = false)
    private UUID flagId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(nullable = false)
    private boolean enabled;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FeatureFlagOverride() {
    }

    public FeatureFlagOverride(UUID flagId, String userId, boolean enabled) {
        this.flagId = flagId;
        this.userId = userId;
        this.enabled = enabled;
    }

    public UUID getId() {
        return id;
    }

    public UUID getFlagId() {
        return flagId;
    }

    public void setFlagId(UUID flagId) {
        this.flagId = flagId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
