package com.digitalocean.flagmanagement.cache;

/**
 * Cached evaluation outcome for {@code eval:{flagId}:{flagVersion}:{userId}}.
 * Only {@code enabled} and {@code reason} are stored; request identity comes from the key.
 */
public record CachedEvaluation(
        boolean enabled,
        String reason
) {
}
