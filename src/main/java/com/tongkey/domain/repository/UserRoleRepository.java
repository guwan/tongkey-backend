package com.tongkey.domain.repository;

import com.tongkey.domain.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRoleRepository extends JpaRepository<UserRole, String> {

    List<UserRole> findByUserId(String userId);

    List<UserRole> findByRoleId(String roleId);

    Optional<UserRole> findByUserIdAndRoleId(String userId, String roleId);

    Optional<UserRole> findBySourceIdAndExternalKey(String sourceId, String externalKey);

    void deleteByUserId(String userId);
}
