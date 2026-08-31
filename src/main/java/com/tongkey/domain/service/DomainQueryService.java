package com.tongkey.domain.service;

import com.tongkey.domain.EntityStatus;
import com.tongkey.domain.SourceType;
import com.tongkey.domain.entity.PermissionEntity;
import com.tongkey.domain.entity.RoleEntity;
import com.tongkey.domain.entity.RolePermission;
import com.tongkey.domain.entity.UserEntity;
import com.tongkey.domain.entity.UserRole;
import com.tongkey.domain.repository.PermissionRepository;
import com.tongkey.domain.repository.RolePermissionRepository;
import com.tongkey.domain.repository.RoleRepository;
import com.tongkey.domain.repository.UserRepository;
import com.tongkey.domain.repository.UserRoleRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 核心域查询服务（分页、过滤、关联查询），供管理台与开放 API 复用。
 */
@Service
@Transactional(readOnly = true)
public class DomainQueryService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public DomainQueryService(UserRepository userRepository, RoleRepository roleRepository,
                              PermissionRepository permissionRepository, UserRoleRepository userRoleRepository,
                              RolePermissionRepository rolePermissionRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRoleRepository = userRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    private static PageRequest page(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 500), Sort.by(Sort.Direction.DESC, "updatedAt"));
    }

    public Page<UserEntity> pageUsers(int page, int size, String keyword, SourceType sourceType, EntityStatus status) {
        Specification<UserEntity> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("username")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("displayName"), "")), like)));
            }
            if (sourceType != null) {
                ps.add(cb.equal(root.get("sourceType"), sourceType));
            }
            if (status != null) {
                ps.add(cb.equal(root.get("status"), status));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return userRepository.findAll(spec, page(page, size));
    }

    public Page<RoleEntity> pageRoles(int page, int size, String keyword, SourceType sourceType) {
        Specification<RoleEntity> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(root.get("name")), like)));
            }
            if (sourceType != null) {
                ps.add(cb.equal(root.get("sourceType"), sourceType));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return roleRepository.findAll(spec, page(page, size));
    }

    public Page<PermissionEntity> pagePermissions(int page, int size, String keyword, SourceType sourceType,
                                                  com.tongkey.domain.ResourceType resourceType) {
        Specification<PermissionEntity> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(root.get("name")), like)));
            }
            if (sourceType != null) {
                ps.add(cb.equal(root.get("sourceType"), sourceType));
            }
            if (resourceType != null) {
                ps.add(cb.equal(root.get("resourceType"), resourceType));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return permissionRepository.findAll(spec, page(page, size));
    }

    public List<RoleEntity> rolesOfUser(String userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(UserRole::getRoleId)
                .map(roleRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .toList();
    }

    public List<UserEntity> usersOfRole(String roleId) {
        return userRoleRepository.findByRoleId(roleId).stream()
                .map(UserRole::getUserId)
                .map(userRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .toList();
    }

    public List<PermissionEntity> permissionsOfRole(String roleId) {
        return rolePermissionRepository.findByRoleId(roleId).stream()
                .map(RolePermission::getPermissionId)
                .map(permissionRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .toList();
    }

    public List<UserRole> userRoleLinks(String userId) {
        return userRoleRepository.findByUserId(userId);
    }

    public List<RolePermission> rolePermissionLinks(String roleId) {
        return rolePermissionRepository.findByRoleId(roleId);
    }
}
