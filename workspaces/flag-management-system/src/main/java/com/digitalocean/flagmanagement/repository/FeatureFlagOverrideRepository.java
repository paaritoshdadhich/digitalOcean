package com.digitalocean.flagmanagement.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.digitalocean.flagmanagement.entity.FeatureFlagOverride;

public interface FeatureFlagOverrideRepository extends JpaRepository<FeatureFlagOverride, UUID> {

    /**
     * Returns empty when no override exists for the user.
     * An explicit {@code enabled=false} override is represented by a present value.
     */
    Optional<FeatureFlagOverride> findByFlagIdAndUserId(UUID flagId, String userId);

    List<FeatureFlagOverride> findByFlagId(UUID flagId);

    boolean existsByFlagIdAndUserId(UUID flagId, String userId);

    void deleteByFlagIdAndUserId(UUID flagId, String userId);
}
