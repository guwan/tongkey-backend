package com.tongkey.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 角色-权限关联表（带来源追溯）。
 */
@Entity
@Table(name = "tk_role_permission",
        uniqueConstraints = @UniqueConstraint(name = "uk_role_permission", columnNames = {"role_id", "permission_id"}),
        indexes = @Index(name = "idx_rp_permission", columnList = "permission_id"))
public class RolePermission extends TraceableEntity {

    @Column(name = "role_id", nullable = false, length = 36)
    private String roleId;

    @Column(name = "permission_id", nullable = false, length = 36)
    private String permissionId;

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(String permissionId) {
        this.permissionId = permissionId;
    }
}
