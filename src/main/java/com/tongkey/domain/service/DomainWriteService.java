package com.tongkey.domain.service;

import com.tongkey.common.ApiException;
import com.tongkey.common.ErrorCode;
import com.tongkey.common.OperatorContext;
import com.tongkey.domain.ChangeAction;
import com.tongkey.domain.ConflictStrategy;
import com.tongkey.domain.EntityStatus;
import com.tongkey.domain.EntityType;
import com.tongkey.domain.ResourceType;
import com.tongkey.domain.SourceType;
import com.tongkey.domain.entity.PermissionEntity;
import com.tongkey.domain.entity.RoleEntity;
import com.tongkey.domain.entity.RolePermission;
import com.tongkey.domain.entity.UserEntity;
import com.tongkey.domain.entity.UserRole;
import com.tongkey.domain.event.EntityChangedEvent;
import com.tongkey.domain.repository.PermissionRepository;
import com.tongkey.domain.repository.RolePermissionRepository;
import com.tongkey.domain.repository.RoleRepository;
import com.tongkey.domain.repository.UserRepository;
import com.tongkey.domain.repository.UserRoleRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 核心域统一写路径：管理台 / 开放 API / 同步引擎的所有写操作都经过这里，
 * 保证来源追溯、审计记录、领域事件发布的一致性（规格文档 4.1 / 6.2）。
 */
@Service
public class DomainWriteService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final AuditService auditService;
    private final ApplicationEventPublisher publisher;

    public DomainWriteService(UserRepository userRepository, RoleRepository roleRepository,
                              PermissionRepository permissionRepository, UserRoleRepository userRoleRepository,
                              RolePermissionRepository rolePermissionRepository, AuditService auditService,
                              ApplicationEventPublisher publisher) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRoleRepository = userRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.auditService = auditService;
        this.publisher = publisher;
    }

    // ============================== USER ==============================

    @Transactional
    public UserEntity createUser(String username, String displayName, EntityStatus status, String extraAttrs) {
        return createUser(username, displayName, status, extraAttrs, null);
    }

    @Transactional
    public UserEntity createUser(String username, String displayName, EntityStatus status, String extraAttrs,
                                 String externalKey) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new ApiException(ErrorCode.DUPLICATE_KEY, "用户名已存在: " + username);
        }
        UserEntity u = new UserEntity();
        u.setUsername(username);
        u.setDisplayName(displayName);
        if (status != null) {
            u.setStatus(status);
        }
        u.setExtraAttrs(extraAttrs);
        u.setExternalKey(externalKey);
        u.setSourceType(currentSourceType());
        u.setCreatedBy(OperatorContext.composedOperator());
        u.setUpdatedBy(u.getCreatedBy());
        u = userRepository.save(u);
        afterWrite(EntityType.USER, ChangeAction.CREATE, u.getId(), username, snapshot(u));
        return u;
    }

    @Transactional
    public UserEntity updateUser(String id, String displayName, EntityStatus status, String extraAttrs) {
        UserEntity u = requireUser(id);
        if (displayName != null) {
            u.setDisplayName(displayName);
        }
        if (status != null) {
            u.setStatus(status);
        }
        if (extraAttrs != null) {
            u.setExtraAttrs(extraAttrs);
        }
        u.setUpdatedBy(OperatorContext.composedOperator());
        u = userRepository.save(u);
        afterWrite(EntityType.USER, ChangeAction.UPDATE, u.getId(), u.getUsername(), snapshot(u));
        return u;
    }

    @Transactional
    public void deleteUser(String id) {
        UserEntity u = requireUser(id);
        userRoleRepository.deleteByUserId(id);
        userRepository.delete(u);
        afterWrite(EntityType.USER, ChangeAction.DELETE, id, u.getUsername(), null);
    }

    public UserEntity requireUser(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "用户不存在: " + id));
    }

    /** 同步写入用户（upsert + 冲突策略），返回实际执行的动作。 */
    @Transactional
    public ChangeAction upsertUserFromSync(String sourceId, String externalKey, Map<String, Object> fields,
                                           ConflictStrategy strategy) {
        Optional<UserEntity> existing = userRepository.findBySourceIdAndExternalKey(sourceId, externalKey);
        UserEntity u = existing.orElseGet(UserEntity::new);
        if (existing.isPresent() && shouldSkip(existing.get(), strategy)) {
            return null; // SKIP_IF_MODIFIED：本地手工修改过，保留本地
        }
        boolean isNew = existing.isEmpty();
        u.setSourceType(SourceType.SYNCED);
        u.setSourceId(sourceId);
        u.setExternalKey(externalKey);
        applyUserFields(u, fields, strategy, existing.isPresent());
        String op = OperatorContext.composedOperator();
        if (isNew) {
            u.setCreatedBy(op);
        }
        u.setUpdatedBy(op);
        u = userRepository.save(u);
        ChangeAction action = isNew ? ChangeAction.CREATE : ChangeAction.UPDATE;
        afterWrite(EntityType.USER, action, u.getId(), u.getUsername(), snapshot(u));
        return action;
    }

    private void applyUserFields(UserEntity u, Map<String, Object> f, ConflictStrategy strategy, boolean exists) {
        String username = str(f.get("username"));
        if (username != null && (!exists || strategy != ConflictStrategy.MERGE_FIELD_LEVEL || u.getUsername() == null)) {
            u.setUsername(username);
        }
        if (u.getUsername() == null) {
            u.setUsername(str(f.get("external_key"))); // 兜底
        }
        String displayName = str(f.get("display_name"));
        if (displayName != null && (!exists || strategy != ConflictStrategy.MERGE_FIELD_LEVEL || u.getDisplayName() == null)) {
            u.setDisplayName(displayName);
        }
        EntityStatus status = parseStatus(f.get("status"));
        if (status != null && (!exists || strategy != ConflictStrategy.MERGE_FIELD_LEVEL)) {
            u.setStatus(status);
        }
        String extra = str(f.get("extra_attrs"));
        if (extra != null && (!exists || strategy != ConflictStrategy.MERGE_FIELD_LEVEL || u.getExtraAttrs() == null)) {
            u.setExtraAttrs(extra);
        }
    }

    // ============================== ROLE ==============================

    @Transactional
    public RoleEntity createRole(String code, String name, String description, String extraAttrs) {
        return createRole(code, name, description, extraAttrs, null);
    }

    @Transactional
    public RoleEntity createRole(String code, String name, String description, String extraAttrs, String externalKey) {
        if (roleRepository.findByCode(code).isPresent()) {
            throw new ApiException(ErrorCode.DUPLICATE_KEY, "角色编码已存在: " + code);
        }
        RoleEntity r = new RoleEntity();
        r.setCode(code);
        r.setName(name);
        r.setDescription(description);
        r.setExtraAttrs(extraAttrs);
        r.setExternalKey(externalKey);
        r.setSourceType(currentSourceType());
        r.setCreatedBy(OperatorContext.composedOperator());
        r.setUpdatedBy(r.getCreatedBy());
        r = roleRepository.save(r);
        afterWrite(EntityType.ROLE, ChangeAction.CREATE, r.getId(), code, snapshot(r));
        return r;
    }

    @Transactional
    public RoleEntity updateRole(String id, String name, String description, String extraAttrs) {
        RoleEntity r = requireRole(id);
        if (name != null) {
            r.setName(name);
        }
        if (description != null) {
            r.setDescription(description);
        }
        if (extraAttrs != null) {
            r.setExtraAttrs(extraAttrs);
        }
        r.setUpdatedBy(OperatorContext.composedOperator());
        r = roleRepository.save(r);
        afterWrite(EntityType.ROLE, ChangeAction.UPDATE, r.getId(), r.getCode(), snapshot(r));
        return r;
    }

    @Transactional
    public void deleteRole(String id) {
        RoleEntity r = requireRole(id);
        userRoleRepository.findByRoleId(id).forEach(userRoleRepository::delete);
        rolePermissionRepository.deleteByRoleId(id);
        roleRepository.delete(r);
        afterWrite(EntityType.ROLE, ChangeAction.DELETE, id, r.getCode(), null);
    }

    public RoleEntity requireRole(String id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "角色不存在: " + id));
    }

    @Transactional
    public ChangeAction upsertRoleFromSync(String sourceId, String externalKey, Map<String, Object> fields,
                                           ConflictStrategy strategy) {
        Optional<RoleEntity> existing = roleRepository.findBySourceIdAndExternalKey(sourceId, externalKey);
        RoleEntity r = existing.orElseGet(RoleEntity::new);
        if (existing.isPresent() && shouldSkip(existing.get(), strategy)) {
            return null;
        }
        boolean isNew = existing.isEmpty();
        r.setSourceType(SourceType.SYNCED);
        r.setSourceId(sourceId);
        r.setExternalKey(externalKey);
        String code = str(fields.get("code"));
        if (code != null && (!existing.isPresent() || strategy != ConflictStrategy.MERGE_FIELD_LEVEL || r.getCode() == null)) {
            r.setCode(code);
        }
        if (r.getCode() == null) {
            r.setCode(externalKey);
        }
        String name = str(fields.get("name"));
        if (name != null && (!existing.isPresent() || strategy != ConflictStrategy.MERGE_FIELD_LEVEL || r.getName() == null)) {
            r.setName(name);
        }
        if (r.getName() == null) {
            r.setName(r.getCode());
        }
        String desc = str(fields.get("description"));
        if (desc != null && (!existing.isPresent() || strategy != ConflictStrategy.MERGE_FIELD_LEVEL || r.getDescription() == null)) {
            r.setDescription(desc);
        }
        String extra = str(fields.get("extra_attrs"));
        if (extra != null && (!existing.isPresent() || strategy != ConflictStrategy.MERGE_FIELD_LEVEL || r.getExtraAttrs() == null)) {
            r.setExtraAttrs(extra);
        }
        String op = OperatorContext.composedOperator();
        if (isNew) {
            r.setCreatedBy(op);
        }
        r.setUpdatedBy(op);
        r = roleRepository.save(r);
        ChangeAction action = isNew ? ChangeAction.CREATE : ChangeAction.UPDATE;
        afterWrite(EntityType.ROLE, action, r.getId(), r.getCode(), snapshot(r));
        return action;
    }

    // ============================== PERMISSION ==============================

    @Transactional
    public PermissionEntity createPermission(String code, String name, String description,
                                             ResourceType resourceType, String extraAttrs) {
        return createPermission(code, name, description, resourceType, extraAttrs, null);
    }

    @Transactional
    public PermissionEntity createPermission(String code, String name, String description,
                                             ResourceType resourceType, String extraAttrs, String externalKey) {
        if (permissionRepository.findByCode(code).isPresent()) {
            throw new ApiException(ErrorCode.DUPLICATE_KEY, "权限编码已存在: " + code);
        }
        PermissionEntity p = new PermissionEntity();
        p.setCode(code);
        p.setName(name);
        p.setDescription(description);
        if (resourceType != null) {
            p.setResourceType(resourceType);
        }
        p.setExtraAttrs(extraAttrs);
        p.setExternalKey(externalKey);
        p.setSourceType(currentSourceType());
        p.setCreatedBy(OperatorContext.composedOperator());
        p.setUpdatedBy(p.getCreatedBy());
        p = permissionRepository.save(p);
        afterWrite(EntityType.PERMISSION, ChangeAction.CREATE, p.getId(), code, snapshot(p));
        return p;
    }

    @Transactional
    public PermissionEntity updatePermission(String id, String name, String description,
                                             ResourceType resourceType, String extraAttrs) {
        PermissionEntity p = requirePermission(id);
        if (name != null) {
            p.setName(name);
        }
        if (description != null) {
            p.setDescription(description);
        }
        if (resourceType != null) {
            p.setResourceType(resourceType);
        }
        if (extraAttrs != null) {
            p.setExtraAttrs(extraAttrs);
        }
        p.setUpdatedBy(OperatorContext.composedOperator());
        p = permissionRepository.save(p);
        afterWrite(EntityType.PERMISSION, ChangeAction.UPDATE, p.getId(), p.getCode(), snapshot(p));
        return p;
    }

    @Transactional
    public void deletePermission(String id) {
        PermissionEntity p = requirePermission(id);
        rolePermissionRepository.findByPermissionId(id).forEach(rolePermissionRepository::delete);
        permissionRepository.delete(p);
        afterWrite(EntityType.PERMISSION, ChangeAction.DELETE, id, p.getCode(), null);
    }

    public PermissionEntity requirePermission(String id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "权限不存在: " + id));
    }

    @Transactional
    public ChangeAction upsertPermissionFromSync(String sourceId, String externalKey, Map<String, Object> fields,
                                                 ConflictStrategy strategy) {
        Optional<PermissionEntity> existing = permissionRepository.findBySourceIdAndExternalKey(sourceId, externalKey);
        PermissionEntity p = existing.orElseGet(PermissionEntity::new);
        if (existing.isPresent() && shouldSkip(existing.get(), strategy)) {
            return null;
        }
        boolean isNew = existing.isEmpty();
        p.setSourceType(SourceType.SYNCED);
        p.setSourceId(sourceId);
        p.setExternalKey(externalKey);
        String code = str(fields.get("code"));
        if (code != null && (!existing.isPresent() || strategy != ConflictStrategy.MERGE_FIELD_LEVEL || p.getCode() == null)) {
            p.setCode(code);
        }
        if (p.getCode() == null) {
            p.setCode(externalKey);
        }
        String name = str(fields.get("name"));
        if (name != null && (!existing.isPresent() || strategy != ConflictStrategy.MERGE_FIELD_LEVEL || p.getName() == null)) {
            p.setName(name);
        }
        if (p.getName() == null) {
            p.setName(p.getCode());
        }
        String desc = str(fields.get("description"));
        if (desc != null && (!existing.isPresent() || strategy != ConflictStrategy.MERGE_FIELD_LEVEL || p.getDescription() == null)) {
            p.setDescription(desc);
        }
        ResourceType rt = parseResourceType(fields.get("resource_type"));
        if (rt != null && (!existing.isPresent() || strategy != ConflictStrategy.MERGE_FIELD_LEVEL)) {
            p.setResourceType(rt);
        }
        String extra = str(fields.get("extra_attrs"));
        if (extra != null && (!existing.isPresent() || strategy != ConflictStrategy.MERGE_FIELD_LEVEL || p.getExtraAttrs() == null)) {
            p.setExtraAttrs(extra);
        }
        String op = OperatorContext.composedOperator();
        if (isNew) {
            p.setCreatedBy(op);
        }
        p.setUpdatedBy(op);
        p = permissionRepository.save(p);
        ChangeAction action = isNew ? ChangeAction.CREATE : ChangeAction.UPDATE;
        afterWrite(EntityType.PERMISSION, action, p.getId(), p.getCode(), snapshot(p));
        return action;
    }

    // ============================== 关联关系 ==============================

    @Transactional
    public UserRole bindUserRole(String userId, String roleId) {
        requireUser(userId);
        requireRole(roleId);
        if (userRoleRepository.findByUserIdAndRoleId(userId, roleId).isPresent()) {
            throw new ApiException(ErrorCode.DUPLICATE_KEY, "用户已绑定该角色");
        }
        UserRole ur = new UserRole();
        ur.setUserId(userId);
        ur.setRoleId(roleId);
        ur.setSourceType(currentSourceType());
        ur.setCreatedBy(OperatorContext.composedOperator());
        ur.setUpdatedBy(ur.getCreatedBy());
        ur = userRoleRepository.save(ur);
        afterWrite(EntityType.USER_ROLE, ChangeAction.CREATE, ur.getId(), userId + "->" + roleId, snapshot(ur));
        return ur;
    }

    @Transactional
    public void unbindUserRole(String userId, String roleId) {
        UserRole ur = userRoleRepository.findByUserIdAndRoleId(userId, roleId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "绑定关系不存在"));
        userRoleRepository.delete(ur);
        afterWrite(EntityType.USER_ROLE, ChangeAction.DELETE, ur.getId(), userId + "->" + roleId, null);
    }

    @Transactional
    public RolePermission bindRolePermission(String roleId, String permissionId) {
        requireRole(roleId);
        requirePermission(permissionId);
        if (rolePermissionRepository.findByRoleIdAndPermissionId(roleId, permissionId).isPresent()) {
            throw new ApiException(ErrorCode.DUPLICATE_KEY, "角色已绑定该权限");
        }
        RolePermission rp = new RolePermission();
        rp.setRoleId(roleId);
        rp.setPermissionId(permissionId);
        rp.setSourceType(currentSourceType());
        rp.setCreatedBy(OperatorContext.composedOperator());
        rp.setUpdatedBy(rp.getCreatedBy());
        rp = rolePermissionRepository.save(rp);
        afterWrite(EntityType.ROLE_PERMISSION, ChangeAction.CREATE, rp.getId(), roleId + "->" + permissionId, snapshot(rp));
        return rp;
    }

    @Transactional
    public void unbindRolePermission(String roleId, String permissionId) {
        RolePermission rp = rolePermissionRepository.findByRoleIdAndPermissionId(roleId, permissionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "绑定关系不存在"));
        rolePermissionRepository.delete(rp);
        afterWrite(EntityType.ROLE_PERMISSION, ChangeAction.DELETE, rp.getId(), roleId + "->" + permissionId, null);
    }

    /** 同步用户-角色关联：通过双方 external_key 解析本地实体后建立关联。 */
    @Transactional
    public ChangeAction upsertUserRoleFromSync(String sourceId, String externalKey,
                                               String userId, String roleId) {
        if (userRoleRepository.findByUserIdAndRoleId(userId, roleId).isPresent()) {
            return null;
        }
        UserRole ur = new UserRole();
        ur.setUserId(userId);
        ur.setRoleId(roleId);
        ur.setSourceType(SourceType.SYNCED);
        ur.setSourceId(sourceId);
        ur.setExternalKey(externalKey);
        ur.setCreatedBy(OperatorContext.composedOperator());
        ur.setUpdatedBy(ur.getCreatedBy());
        ur = userRoleRepository.save(ur);
        afterWrite(EntityType.USER_ROLE, ChangeAction.CREATE, ur.getId(), userId + "->" + roleId, snapshot(ur));
        return ChangeAction.CREATE;
    }

    @Transactional
    public ChangeAction upsertRolePermissionFromSync(String sourceId, String externalKey,
                                                     String roleId, String permissionId) {
        if (rolePermissionRepository.findByRoleIdAndPermissionId(roleId, permissionId).isPresent()) {
            return null;
        }
        RolePermission rp = new RolePermission();
        rp.setRoleId(roleId);
        rp.setPermissionId(permissionId);
        rp.setSourceType(SourceType.SYNCED);
        rp.setSourceId(sourceId);
        rp.setExternalKey(externalKey);
        rp.setCreatedBy(OperatorContext.composedOperator());
        rp.setUpdatedBy(rp.getCreatedBy());
        rp = rolePermissionRepository.save(rp);
        afterWrite(EntityType.ROLE_PERMISSION, ChangeAction.CREATE, rp.getId(), roleId + "->" + permissionId, snapshot(rp));
        return ChangeAction.CREATE;
    }

    // ============================== 公共逻辑 ==============================

    /** 渠道决定来源类型：开放 API 写入标记为 API（规格文档 7.1），其余为 NATIVE；同步路径会显式覆盖为 SYNCED。 */
    private static SourceType currentSourceType() {
        return OperatorContext.channel().startsWith("API:") ? SourceType.API : SourceType.NATIVE;
    }

    /** SYNC_SKIP_IF_MODIFIED：本地记录最近一次修改不是同步写入的 → 跳过。 */
    private boolean shouldSkip(com.tongkey.domain.entity.TraceableEntity e, ConflictStrategy strategy) {
        if (strategy != ConflictStrategy.SYNC_SKIP_IF_MODIFIED) {
            return false;
        }
        String updatedBy = e.getUpdatedBy();
        return updatedBy != null && !updatedBy.startsWith("SYNC:");
    }

    private void afterWrite(EntityType type, ChangeAction action, String id, String code, Map<String, Object> snapshot) {
        auditService.record(OperatorContext.channel(), OperatorContext.operator(), type, id, code, action, null);
        publisher.publishEvent(new EntityChangedEvent(type, action, id, code, snapshot,
                OperatorContext.channel(), Instant.now()));
    }

    // ============================== 快照 ==============================

    public static Map<String, Object> snapshot(UserEntity u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("display_name", u.getDisplayName());
        m.put("status", u.getStatus() == null ? null : u.getStatus().name());
        m.put("source_type", u.getSourceType().name());
        m.put("source_id", u.getSourceId());
        m.put("external_key", u.getExternalKey());
        m.put("extra_attrs", u.getExtraAttrs());
        m.put("updated_at", u.getUpdatedAt() == null ? null : u.getUpdatedAt().toString());
        return m;
    }

    public static Map<String, Object> snapshot(RoleEntity r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("code", r.getCode());
        m.put("name", r.getName());
        m.put("description", r.getDescription());
        m.put("source_type", r.getSourceType().name());
        m.put("source_id", r.getSourceId());
        m.put("external_key", r.getExternalKey());
        m.put("extra_attrs", r.getExtraAttrs());
        m.put("updated_at", r.getUpdatedAt() == null ? null : r.getUpdatedAt().toString());
        return m;
    }

    public static Map<String, Object> snapshot(PermissionEntity p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("code", p.getCode());
        m.put("name", p.getName());
        m.put("description", p.getDescription());
        m.put("resource_type", p.getResourceType().name());
        m.put("source_type", p.getSourceType().name());
        m.put("source_id", p.getSourceId());
        m.put("external_key", p.getExternalKey());
        m.put("extra_attrs", p.getExtraAttrs());
        m.put("updated_at", p.getUpdatedAt() == null ? null : p.getUpdatedAt().toString());
        return m;
    }

    public static Map<String, Object> snapshot(UserRole ur) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", ur.getId());
        m.put("user_id", ur.getUserId());
        m.put("role_id", ur.getRoleId());
        m.put("source_type", ur.getSourceType().name());
        return m;
    }

    public static Map<String, Object> snapshot(RolePermission rp) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rp.getId());
        m.put("role_id", rp.getRoleId());
        m.put("permission_id", rp.getPermissionId());
        m.put("source_type", rp.getSourceType().name());
        return m;
    }

    private static String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = o.toString();
        return s.isBlank() ? null : s;
    }

    private static EntityStatus parseStatus(Object o) {
        if (o == null) {
            return null;
        }
        String s = o.toString().trim().toLowerCase();
        return switch (s) {
            case "1", "true", "y", "yes", "enabled", "启用", "有效" -> EntityStatus.ENABLED;
            case "0", "false", "n", "no", "disabled", "禁用", "无效" -> EntityStatus.DISABLED;
            default -> null;
        };
    }

    private static ResourceType parseResourceType(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return ResourceType.valueOf(o.toString().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
