package com.digitalocean.flagmanagement.service;

import java.util.Map;
import java.util.Optional;

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
    private final AuditLogService auditLogService;
    private final EntityManager entityManager;
    private final AfterCommitCacheInvalidator cacheInvalidator;

    public FeatureFlagOverrideService(
            FeatureFlagRepository featureFlagRepository,
            FeatureFlagOverrideRepository overrideRepository,
            AuditLogService auditLogService,
            EntityManager entityManager,
            AfterCommitCacheInvalidator cacheInvalidator
    ) {
        this.featureFlagRepository = featureFlagRepository;
        this.overrideRepository = overrideRepository;
        this.auditLogService = auditLogService;
        this.entityManager = entityManager;
        this.cacheInvalidator = cacheInvalidator;
    }

    /**
     * Upserts a user override and bumps the parent flag version in the same transaction
     * so versioned eval keys from prior configurations are abandoned without Redis scans.
     */
    @Transactional
    public FeatureFlagOverrideResponse upsert(
            String flagName,
            String userId,
            UpsertOverrideRequest request,
            String actor
    ) {
        FeatureFlag flag = getFlagOrThrow(flagName);

        Optional<FeatureFlagOverride> existing =
                overrideRepository.findByFlagIdAndUserId(flag.getId(), userId);
        Map<String, Object> oldValue = existing
                .map(AuditLogService::overrideValue)
                .orElse(null);
        AuditAction action = existing.isPresent() ? AuditAction.UPDATE : AuditAction.CREATE;

        FeatureFlagOverride override = existing.orElseGet(
                () -> new FeatureFlagOverride(flag.getId(), userId, request.enabled())
        );
        override.setEnabled(request.enabled());
        FeatureFlagOverride saved = overrideRepository.save(override);

        bumpFlagVersion(flag);
        auditLogService.record(
                flag.getId(),
                actor,
                action,
                oldValue,
                AuditLogService.overrideValue(saved)
        );
        cacheInvalidator.invalidateFlagConfig(flag.getName());

        return new FeatureFlagOverrideResponse(flag.getName(), userId, saved.isEnabled());
    }

    @Transactional
    public void delete(String flagName, String userId, String actor) {
        FeatureFlag flag = getFlagOrThrow(flagName);

        FeatureFlagOverride override = overrideRepository
                .findByFlagIdAndUserId(flag.getId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Override not found for flag '" + flagName + "' and user '" + userId + "'"
                ));

        Map<String, Object> oldValue = AuditLogService.overrideValue(override);
        overrideRepository.delete(override);
        bumpFlagVersion(flag);
        auditLogService.record(
                flag.getId(),
                actor,
                AuditAction.DELETE,
                oldValue,
                null
        );
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
