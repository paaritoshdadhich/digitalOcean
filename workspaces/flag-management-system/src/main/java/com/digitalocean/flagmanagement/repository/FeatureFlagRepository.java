package com.digitalocean.flagmanagement.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.digitalocean.flagmanagement.entity.FeatureFlag;

public interface FeatureFlagRepository extends JpaRepository<FeatureFlag, UUID> {

    Optional<FeatureFlag> findByName(String name);

    boolean existsByName(String name);
}
