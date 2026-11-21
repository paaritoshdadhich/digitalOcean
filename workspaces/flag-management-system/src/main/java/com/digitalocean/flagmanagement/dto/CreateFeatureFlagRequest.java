package com.digitalocean.flagmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateFeatureFlagRequest(
        @NotBlank
        @Pattern(
                regexp = "^[a-zA-Z][a-zA-Z0-9_-]{0,99}$",
                message = "must match ^[a-zA-Z][a-zA-Z0-9_-]{0,99}$"
        )
        String name,

        @Size(max = 500)
        String description,

        @NotNull
        Boolean enabled
) {
}
