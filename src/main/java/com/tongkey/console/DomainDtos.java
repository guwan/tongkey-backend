package com.tongkey.console;

import com.tongkey.domain.entity.PermissionEntity;
import com.tongkey.domain.entity.RoleEntity;
import com.tongkey.domain.entity.RolePermission;
import com.tongkey.domain.entity.UserEntity;
import com.tongkey.domain.entity.UserRole;

import java.time.Instant;
import java.util.List;

/**
 * 管理控制台域对象 DTO。
 */
public final class DomainDtos {

    private DomainDtos() {
    }

    public record UserRequest(String username, String displayName, String status, String extraAttrs) {
    }

    public record UserView(String id, String username, String displayName, String status, String sourceType,
                           String sourceId, String externalKey, String extraAttrs,
                           Instant createdAt, Instant updatedAt, String createdBy, String updatedBy) {
        public static UserView of(UserEntity u) {
            return new UserView(u.getId(), u.getUsername(), u.getDisplayName(), u.getStatus().name(),
                    u.getSourceType().name(), u.getSourceId(), u.getExternalKey(), u.getExtraAttrs(),
                    u.getCreatedAt(), u.getUpdatedAt(), u.getCreatedBy(), u.getUpdatedBy());
        }
    }

    public record RoleRequest(String code, String name, String description, String extraAttrs) {
    }

    public record RoleView(String id, String code, String name, String description, String sourceType,
                           String sourceId, String externalKey, String extraAttrs,
                           Instant createdAt, Instant updatedAt, String createdBy, String updatedBy) {
        public static RoleView of(RoleEntity r) {
            return new RoleView(r.getId(), r.getCode(), r.getName(), r.getDescription(),
                    r.getSourceType().name(), r.getSourceId(), r.getExternalKey(), r.getExtraAttrs(),
                    r.getCreatedAt(), r.getUpdatedAt(), r.getCreatedBy(), r.getUpdatedBy());
        }
    }

    public record PermissionRequest(String code, String name, String description, String resourceType, String extraAttrs) {
    }

    public record PermissionView(String id, String code, String name, String description, String resourceType,
                                 String sourceType, String sourceId, String externalKey, String extraAttrs,
                                 Instant createdAt, Instant updatedAt, String createdBy, String updatedBy) {
        public static PermissionView of(PermissionEntity p) {
            return new PermissionView(p.getId(), p.getCode(), p.getName(), p.getDescription(),
                    p.getResourceType().name(), p.getSourceType().name(), p.getSourceId(), p.getExternalKey(),
                    p.getExtraAttrs(), p.getCreatedAt(), p.getUpdatedAt(), p.getCreatedBy(), p.getUpdatedBy());
        }
    }

    public record LinkView(String id, String userId, String roleId, String permissionId, String sourceType, Instant createdAt) {
        public static LinkView of(UserRole ur) {
            return new LinkView(ur.getId(), ur.getUserId(), ur.getRoleId(), null, ur.getSourceType().name(), ur.getCreatedAt());
        }

        public static LinkView of(RolePermission rp) {
            return new LinkView(rp.getId(), null, rp.getRoleId(), rp.getPermissionId(), rp.getSourceType().name(), rp.getCreatedAt());
        }
    }

    public record BindRequest(String roleId, String permissionId) {
    }

    /** 用户详情（含角色列表），用于用户-角色管理界面。 */
    public record UserDetail(UserView user, List<RoleView> roles) {
    }

    /** 角色详情（含权限列表），用于角色-权限管理界面。 */
    public record RoleDetail(RoleView role, List<PermissionView> permissions) {
    }
}
