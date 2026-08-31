package com.tongkey.domain.repository;

import com.tongkey.domain.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RolePermissionRepository extends JpaRepository<RolePermission, String> {

    List<RolePermission> findByRoleId(String roleId);

    List<RolePermission> findByPermissionId(String permissionId);

    Optional<RolePermission> findByRoleIdAndPermissionId(String roleId, String permissionId);

    Optional<RolePermission> findBySourceIdAndExternalKey(String sourceId, String externalKey);

    void deleteByRoleId(String roleId);
}
