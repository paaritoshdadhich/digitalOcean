package com.digitalocean.flagmanagement.dto;

public record FeatureFlagOverrideResponse(
        String flagName,
        String userId,
        boolean enabled
) {
}
