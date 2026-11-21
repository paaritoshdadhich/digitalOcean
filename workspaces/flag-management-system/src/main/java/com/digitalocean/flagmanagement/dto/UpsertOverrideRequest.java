package com.digitalocean.flagmanagement.dto;

import jakarta.validation.constraints.NotNull;

public record UpsertOverrideRequest(
        @NotNull
        Boolean enabled
) {
}
