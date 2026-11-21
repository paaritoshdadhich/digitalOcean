package com.digitalocean.flagmanagement.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.digitalocean.flagmanagement.entity.AuditLog;
import com.digitalocean.flagmanagement.entity.FeatureFlag;
import com.digitalocean.flagmanagement.entity.FeatureFlagOverride;
import com.digitalocean.flagmanagement.repository.AuditLogRepository;

/**
 * Writes audit rows in the caller's transaction so a rollback also removes the audit entry.
 */
@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(
            UUID flagId,
            String actor,
            AuditAction action,
            Map<String, Object> oldValue,
            Map<String, Object> newValue
    ) {
        auditLogRepository.save(new AuditLog(
                flagId,
                actor,
                action.name(),
                oldValue,
                newValue
        ));
    }

    public static Map<String, Object> flagValue(FeatureFlag flag) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("name", flag.getName());
        value.put("description", flag.getDescription());
        value.put("enabled", flag.isEnabled());
        return value;
    }

    public static Map<String, Object> overrideValue(FeatureFlagOverride override) {
        return overrideValue(override.getUserId(), override.isEnabled());
    }

    public static Map<String, Object> overrideValue(String userId, boolean enabled) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("userId", userId);
        value.put("enabled", enabled);
        return value;
    }
}
