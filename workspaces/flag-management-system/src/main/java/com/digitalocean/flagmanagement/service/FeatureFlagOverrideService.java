package com.digitalocean.flagmanagement.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalocean.flagmanagement.cache.AfterCommitCacheInvalidator;
import com.digitalocean.flagmanagement.dto.FeatureFlagOverrideResponse;
import com.digitalocean.flagmanagement.dto.UpsertOverrideRequest;
import com.digitalocean.flagmanagement.entity.FeatureFlag;
import com.digitalocean.flagmanagement.entity.FeatureFlagOverride;
import com.digitalocean.flagmanagement.exception.ResourceNotFoundException;
import com.digitalocean.flagmanagement.repository.FeatureFlagOverrideRepository;
import com.digitalocean.flagmanagement.repository.FeatureFlagRepository;

@Service
public class FeatureFlagOverrideService {

    private final FeatureFlagRepository featureFlagRepository;
    private final FeatureFlagOverrideRepository overrideRepository;
    private final EntityManager entityManager;
    private final AfterCommitCacheInvalidator cacheInvalidator;

    public FeatureFlagOverrideService(
            FeatureFlagRepository featureFlagRepository,
            FeatureFlagOverrideRepository overrideRepository,
            EntityManager entityManager,
            AfterCommitCacheInvalidator cacheInvalidator
    ) {
        this.featureFlagRepository = featureFlagRepository;
        this.overrideRepository = overrideRepository;
        this.entityManager = entityManager;
        this.cacheInvalidator = cacheInvalidator;
    }

    /**
     * Upserts a user override and bumps the parent flag version in the same transaction
     * so versioned eval keys from prior configurations are abandoned without Redis scans.
     */
    @Transactional
    public FeatureFlagOverrideResponse upsert(String flagName, String userId, UpsertOverrideRequest request) {
        FeatureFlag flag = getFlagOrThrow(flagName);

        FeatureFlagOverride override = overrideRepository
                .findByFlagIdAndUserId(flag.getId(), userId)
                .orElseGet(() -> new FeatureFlagOverride(flag.getId(), userId, request.enabled()));

        override.setEnabled(request.enabled());
        overrideRepository.save(override);
        bumpFlagVersion(flag);
        cacheInvalidator.invalidateFlagConfig(flag.getName());

        return new FeatureFlagOverrideResponse(flag.getName(), userId, override.isEnabled());
    }

    @Transactional
    public void delete(String flagName, String userId) {
        FeatureFlag flag = getFlagOrThrow(flagName);

        if (!overrideRepository.existsByFlagIdAndUserId(flag.getId(), userId)) {
            throw new ResourceNotFoundException(
                    "Override not found for flag '" + flagName + "' and user '" + userId + "'"
            );
        }

        overrideRepository.deleteByFlagIdAndUserId(flag.getId(), userId);
        bumpFlagVersion(flag);
        cacheInvalidator.invalidateFlagConfig(flag.getName());
    }

    private void bumpFlagVersion(FeatureFlag flag) {
        entityManager.lock(flag, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
    }

    private FeatureFlag getFlagOrThrow(String flagName) {
        return featureFlagRepository.findByName(flagName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Feature flag not found: " + flagName
                ));
    }
}
