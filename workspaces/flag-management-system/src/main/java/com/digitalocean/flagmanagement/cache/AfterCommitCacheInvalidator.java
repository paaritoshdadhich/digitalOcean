package com.digitalocean.flagmanagement.cache;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Deletes {@code flag:{flagName}} only after a successful commit so rolled-back writes
 * never invalidate (or race with) the cache, and so we never evict before the new version
 * is durable in PostgreSQL.
 */
@Component
public class AfterCommitCacheInvalidator {

    private final FeatureFlagCacheService cacheService;

    public AfterCommitCacheInvalidator(FeatureFlagCacheService cacheService) {
        this.cacheService = cacheService;
    }

    public void invalidateFlagConfig(String flagName) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cacheService.evictFlagConfig(flagName);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                cacheService.evictFlagConfig(flagName);
            }
        });
    }
}
