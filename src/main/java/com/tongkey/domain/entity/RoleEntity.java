package com.tongkey.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 角色（规格文档 4.1）。
 */
@Entity
@Table(name = "tk_role",
        uniqueConstraints = @UniqueConstraint(name = "uk_role_code", columnNames = "code"),
        indexes = @Index(name = "idx_role_external", columnList = "source_id, external_key"))
public class RoleEntity extends TraceableEntity {

    @Column(nullable = false, length = 128)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 1024)
    private String description;

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
}
