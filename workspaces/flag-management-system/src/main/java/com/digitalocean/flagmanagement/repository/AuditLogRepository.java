package com.digitalocean.flagmanagement.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.digitalocean.flagmanagement.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByFlagIdOrderByCreatedAtDesc(UUID flagId);
}
