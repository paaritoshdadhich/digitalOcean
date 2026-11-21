package com.digitalocean.flagmanagement.dto;

import jakarta.validation.constraints.NotBlank;

public record EvaluateRequest(
        @NotBlank
        String flagName,

        @NotBlank
        String userId
) {
}
