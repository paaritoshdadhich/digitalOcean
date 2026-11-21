package com.digitalocean.flagmanagement.dto;

import java.time.Instant;
import java.util.UUID;

import com.digitalocean.flagmanagement.entity.FeatureFlag;

public record FeatureFlagResponse(
        UUID id,
        String name,
        String description,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {

    public static FeatureFlagResponse from(FeatureFlag flag) {
        return new FeatureFlagResponse(
                flag.getId(),
                flag.getName(),
                flag.getDescription(),
                flag.isEnabled(),
                flag.getCreatedAt(),
                flag.getUpdatedAt()
        );
    }
}
