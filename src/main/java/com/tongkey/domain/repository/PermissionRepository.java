package com.tongkey.domain.repository;

import com.tongkey.domain.entity.PermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface PermissionRepository extends JpaRepository<PermissionEntity, String>, JpaSpecificationExecutor<PermissionEntity> {

    Optional<PermissionEntity> findByCode(String code);

    Optional<PermissionEntity> findBySourceIdAndExternalKey(String sourceId, String externalKey);

    Optional<PermissionEntity> findFirstByExternalKey(String externalKey);

    long countBySourceType(com.tongkey.domain.SourceType sourceType);
}
