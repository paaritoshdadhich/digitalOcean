package com.digitalocean.flagmanagement.cache;

import java.util.UUID;

/**
 * Cached global flag configuration for {@code flag:{flagName}}.
 */
public record CachedFlagConfig(
        UUID id,
        String name,
        boolean enabled,
        long version
) {
}
