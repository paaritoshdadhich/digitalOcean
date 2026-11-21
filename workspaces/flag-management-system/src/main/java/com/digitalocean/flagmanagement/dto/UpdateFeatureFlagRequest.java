package com.digitalocean.flagmanagement.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateFeatureFlagRequest(
        @Size(max = 500)
        String description,

        @NotNull
        Boolean enabled
) {
}
