package com.digitalocean.flagmanagement.dto;

public record EvaluateResponse(
        String flagName,
        String userId,
        boolean enabled,
        String reason
) {
}
