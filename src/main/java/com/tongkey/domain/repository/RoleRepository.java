package com.tongkey.domain.repository;

import com.tongkey.domain.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<RoleEntity, String>, JpaSpecificationExecutor<RoleEntity> {

    Optional<RoleEntity> findByCode(String code);

    Optional<RoleEntity> findBySourceIdAndExternalKey(String sourceId, String externalKey);

    Optional<RoleEntity> findFirstByExternalKey(String externalKey);

    long countBySourceType(com.tongkey.domain.SourceType sourceType);
}
