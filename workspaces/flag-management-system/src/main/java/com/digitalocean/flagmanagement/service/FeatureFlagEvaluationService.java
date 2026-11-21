package com.digitalocean.flagmanagement.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public FeatureFlagEvaluationService(
            FeatureFlagRepository featureFlagRepository,
            FeatureFlagOverrideRepository overrideRepository
    ) {
        this.featureFlagRepository = featureFlagRepository;
        this.overrideRepository = overrideRepository;
    }

    /**
     * Resolves effective flag state for a user.
     * Explicit user overrides always win; absence of an override falls back to global state.
     * A persisted {@code enabled=false} override is never treated as "no override".
     */
    @Transactional(readOnly = true)
    public EvaluateResponse evaluate(EvaluateRequest request) {
        FeatureFlag flag = featureFlagRepository.findByName(request.flagName())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Feature flag not found: " + request.flagName()
                ));

        return overrideRepository.findByFlagIdAndUserId(flag.getId(), request.userId())
                .map(override -> toOverrideResponse(flag, request.userId(), override))
                .orElseGet(() -> toGlobalResponse(flag, request.userId()));
    }

    private EvaluateResponse toOverrideResponse(
            FeatureFlag flag,
            String userId,
            FeatureFlagOverride override
    ) {
        return new EvaluateResponse(
                flag.getName(),
                userId,
                override.isEnabled(),
                EvaluationReason.USER_OVERRIDE.name()
        );
    }

    private EvaluateResponse toGlobalResponse(FeatureFlag flag, String userId) {
        return new EvaluateResponse(
                flag.getName(),
                userId,
                flag.isEnabled(),
                EvaluationReason.GLOBAL.name()
        );
    }
}
