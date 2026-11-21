package com.digitalocean.flagmanagement.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalocean.flagmanagement.cache.AfterCommitCacheInvalidator;
import com.digitalocean.flagmanagement.dto.CreateFeatureFlagRequest;
import com.digitalocean.flagmanagement.dto.FeatureFlagResponse;
import com.digitalocean.flagmanagement.dto.UpdateFeatureFlagRequest;
import com.digitalocean.flagmanagement.entity.FeatureFlag;
import com.digitalocean.flagmanagement.exception.DuplicateResourceException;
import com.digitalocean.flagmanagement.exception.ResourceNotFoundException;
import com.digitalocean.flagmanagement.repository.FeatureFlagRepository;

@Service
public class FeatureFlagService {

    private final FeatureFlagRepository featureFlagRepository;
    private final AuditLogService auditLogService;
    private final AfterCommitCacheInvalidator cacheInvalidator;

    public FeatureFlagService(
            FeatureFlagRepository featureFlagRepository,
            AuditLogService auditLogService,
            AfterCommitCacheInvalidator cacheInvalidator
    ) {
        this.featureFlagRepository = featureFlagRepository;
        this.auditLogService = auditLogService;
        this.cacheInvalidator = cacheInvalidator;
    }

    @Transactional
    public FeatureFlagResponse create(CreateFeatureFlagRequest request, String actor) {
        if (featureFlagRepository.existsByName(request.name())) {
            throw new DuplicateResourceException(
                    "Feature flag already exists: " + request.name()
            );
        }

        FeatureFlag flag = new FeatureFlag(
                request.name(),
                request.description(),
                request.enabled()
        );
        FeatureFlag saved = featureFlagRepository.save(flag);
        auditLogService.record(
                saved.getId(),
                actor,
                AuditAction.CREATE,
                null,
                AuditLogService.flagValue(saved)
        );
        return FeatureFlagResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<FeatureFlagResponse> findAll() {
        return featureFlagRepository.findAll().stream()
                .map(FeatureFlagResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public FeatureFlagResponse findByName(String name) {
        return FeatureFlagResponse.from(getFlagOrThrow(name));
    }

    /**
     * Updates global flag state. JPA {@code @Version} increments on flush; flag config
     * cache is evicted only after commit so readers never see a half-applied write.
     */
    @Transactional
    public FeatureFlagResponse update(String name, UpdateFeatureFlagRequest request, String actor) {
        FeatureFlag flag = getFlagOrThrow(name);
        Map<String, Object> oldValue = AuditLogService.flagValue(flag);

        flag.setDescription(request.description());
        flag.setEnabled(request.enabled());
        FeatureFlag saved = featureFlagRepository.save(flag);

        auditLogService.record(
                saved.getId(),
                actor,
                AuditAction.UPDATE,
                oldValue,
                AuditLogService.flagValue(saved)
        );
        cacheInvalidator.invalidateFlagConfig(saved.getName());
        return FeatureFlagResponse.from(saved);
    }

    @Transactional
    public void delete(String name, String actor) {
        FeatureFlag flag = getFlagOrThrow(name);
        Map<String, Object> oldValue = AuditLogService.flagValue(flag);

        auditLogService.record(
                flag.getId(),
                actor,
                AuditAction.DELETE,
                oldValue,
                null
        );
        featureFlagRepository.delete(flag);
        cacheInvalidator.invalidateFlagConfig(name);
    }

    private FeatureFlag getFlagOrThrow(String name) {
        return featureFlagRepository.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Feature flag not found: " + name
                ));
    }
}
