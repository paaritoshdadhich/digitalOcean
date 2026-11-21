package com.digitalocean.flagmanagement.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalocean.flagmanagement.cache.CachedEvaluation;
import com.digitalocean.flagmanagement.cache.CachedFlagConfig;
import com.digitalocean.flagmanagement.cache.FeatureFlagCacheService;
import com.digitalocean.flagmanagement.dto.EvaluateRequest;
import com.digitalocean.flagmanagement.dto.EvaluateResponse;
import com.digitalocean.flagmanagement.entity.FeatureFlag;
import com.digitalocean.flagmanagement.entity.FeatureFlagOverride;
import com.digitalocean.flagmanagement.exception.ResourceNotFoundException;
import com.digitalocean.flagmanagement.repository.FeatureFlagOverrideRepository;
import com.digitalocean.flagmanagement.repository.FeatureFlagRepository;

@Service
public class FeatureFlagEvaluationService {

    private final FeatureFlagRepository featureFlagRepository;
    private final FeatureFlagOverrideRepository overrideRepository;
    private final FeatureFlagCacheService cacheService;

    public FeatureFlagEvaluationService(
            FeatureFlagRepository featureFlagRepository,
            FeatureFlagOverrideRepository overrideRepository,
            FeatureFlagCacheService cacheService
    ) {
        this.featureFlagRepository = featureFlagRepository;
        this.overrideRepository = overrideRepository;
        this.cacheService = cacheService;
    }

    /**
     * Resolves effective flag state for a user using cache-aside.
     * Explicit user overrides always win; absence of an override falls back to global state.
     * Nonexistent flags are not cached.
     */
    @Transactional(readOnly = true)
    public EvaluateResponse evaluate(EvaluateRequest request) {
        CachedFlagConfig flagConfig = resolveFlagConfig(request.flagName());

        return cacheService
                .getEvaluation(flagConfig.id(), flagConfig.version(), request.userId())
                .map(cached -> toResponse(flagConfig.name(), request.userId(), cached))
                .orElseGet(() -> evaluateAndCache(flagConfig, request.userId()));
    }

    private CachedFlagConfig resolveFlagConfig(String flagName) {
        return cacheService.getFlagConfig(flagName).orElseGet(() -> loadAndCacheFlagConfig(flagName));
    }

    private CachedFlagConfig loadAndCacheFlagConfig(String flagName) {
        FeatureFlag flag = featureFlagRepository.findByName(flagName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Feature flag not found: " + flagName
                ));

        CachedFlagConfig config = new CachedFlagConfig(
                flag.getId(),
                flag.getName(),
                flag.isEnabled(),
                flag.getVersion()
        );
        cacheService.putFlagConfig(config);
        return config;
    }

    private EvaluateResponse evaluateAndCache(CachedFlagConfig flagConfig, String userId) {
        EvaluateResponse response = overrideRepository
                .findByFlagIdAndUserId(flagConfig.id(), userId)
                .map(override -> toOverrideResponse(flagConfig.name(), userId, override))
                .orElseGet(() -> toGlobalResponse(flagConfig, userId));

        cacheService.putEvaluation(
                flagConfig.id(),
                flagConfig.version(),
                userId,
                new CachedEvaluation(response.enabled(), response.reason())
        );
        return response;
    }

    private EvaluateResponse toResponse(String flagName, String userId, CachedEvaluation cached) {
        return new EvaluateResponse(flagName, userId, cached.enabled(), cached.reason());
    }

    private EvaluateResponse toOverrideResponse(
            String flagName,
            String userId,
            FeatureFlagOverride override
    ) {
        return new EvaluateResponse(
                flagName,
                userId,
                override.isEnabled(),
                EvaluationReason.USER_OVERRIDE.name()
        );
    }

    private EvaluateResponse toGlobalResponse(CachedFlagConfig flagConfig, String userId) {
        return new EvaluateResponse(
                flagConfig.name(),
                userId,
                flagConfig.enabled(),
                EvaluationReason.GLOBAL.name()
        );
    }
}
