package com.tongkey.domain.entity;

import com.tongkey.domain.ResourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 权限（规格文档 4.1）。
 */
@Entity
@Table(name = "tk_permission",
        uniqueConstraints = @UniqueConstraint(name = "uk_permission_code", columnNames = "code"),
        indexes = @Index(name = "idx_permission_external", columnList = "source_id, external_key"))
public class PermissionEntity extends TraceableEntity {

    @Column(nullable = false, length = 128)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 1024)
    private String description;

    /** 权限所属资源类型（菜单/接口/按钮/数据权限等） */
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 16)
    private ResourceType resourceType = ResourceType.OTHER;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public void setResourceType(ResourceType resourceType) {
        this.resourceType = resourceType;
    }
}
