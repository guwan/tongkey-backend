package com.tongkey.domain.repository;

import com.tongkey.domain.SourceType;
import com.tongkey.domain.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, String>, JpaSpecificationExecutor<UserEntity> {

    Optional<UserEntity> findByUsername(String username);

    Optional<UserEntity> findBySourceIdAndExternalKey(String sourceId, String externalKey);

    Optional<UserEntity> findFirstByExternalKey(String externalKey);

    long countBySourceType(SourceType sourceType);

    void deleteBySourceType(SourceType sourceType);

    long countBySourceId(String sourceId);

    void deleteBySourceId(String sourceId);
}
