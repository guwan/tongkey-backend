package com.tongkey.domain.entity;

import com.tongkey.domain.ChangeAction;
import com.tongkey.domain.EntityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 全局审计日志：记录谁、何时、通过什么渠道、对哪个实体做了什么变更（规格文档 10）。
 * <p>同时作为开放 API "变更查询"（7.1 /api/v1/changes）的数据来源。</p>
 */
@Entity
@Table(name = "tk_audit_log", indexes = {
        @Index(name = "idx_audit_entity", columnList = "entity_type, created_at"),
        @Index(name = "idx_audit_created", columnList = "created_at")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 变更渠道：CONSOLE / API:{clientId} / SYNC:{数据源名} */
    @Column(nullable = false, length = 128)
    private String channel;

    @Column(nullable = false, length = 128)
    private String operator;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 20)
    private EntityType entityType;

    @Column(name = "entity_id", length = 36)
    private String entityId;

    /** 便于按业务标识检索，如用户名 / 角色编码 / external_key */
    @Column(name = "entity_code", length = 255)
    private String entityCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChangeAction action;

    /** 变更摘要（字段级 JSON，可为空） */
    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getEntityCode() {
        return entityCode;
    }

    public void setEntityCode(String entityCode) {
        this.entityCode = entityCode;
    }

    public ChangeAction getAction() {
        return action;
    }

    public void setAction(ChangeAction action) {
        this.action = action;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
