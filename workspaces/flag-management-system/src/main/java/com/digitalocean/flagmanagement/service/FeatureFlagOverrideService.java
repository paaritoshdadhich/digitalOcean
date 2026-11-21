package com.digitalocean.flagmanagement.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public FeatureFlagOverrideService(
            FeatureFlagRepository featureFlagRepository,
            FeatureFlagOverrideRepository overrideRepository
    ) {
        this.featureFlagRepository = featureFlagRepository;
        this.overrideRepository = overrideRepository;
    }

    @Transactional
    public FeatureFlagOverrideResponse upsert(String flagName, String userId, UpsertOverrideRequest request) {
        FeatureFlag flag = getFlagOrThrow(flagName);

        FeatureFlagOverride override = overrideRepository
                .findByFlagIdAndUserId(flag.getId(), userId)
                .orElseGet(() -> new FeatureFlagOverride(flag.getId(), userId, request.enabled()));

        override.setEnabled(request.enabled());
        overrideRepository.save(override);

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
    }

    private FeatureFlag getFlagOrThrow(String flagName) {
        return featureFlagRepository.findByName(flagName)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Feature flag not found: " + flagName
                ));
    }
}
