package com.digitalocean.flagmanagement.cache;

import java.util.UUID;

public final class FeatureFlagCacheKeys {

    private FeatureFlagCacheKeys() {
    }

    public static String flagConfig(String flagName) {
        return "flag:" + flagName;
    }

    public static String evaluation(UUID flagId, long flagVersion, String userId) {
        return "eval:" + flagId + ":" + flagVersion + ":" + userId;
    }
}
