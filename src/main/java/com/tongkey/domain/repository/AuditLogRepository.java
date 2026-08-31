package com.tongkey.domain.repository;

import com.tongkey.domain.EntityType;
import com.tongkey.domain.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByEntityTypeAndCreatedAtAfterOrderByCreatedAtDesc(EntityType entityType, Instant since, Pageable pageable);

    Page<AuditLog> findByCreatedAtAfterOrderByCreatedAtDesc(Instant since, Pageable pageable);

    Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByCreatedAtAfter(Instant since);
}
