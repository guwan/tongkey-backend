package com.tongkey.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.Instant;
import java.util.UUID;

/**
 * 实体基类：统一主键、时间戳、操作者与来源追溯字段（规格文档 4.1）。
 */
@MappedSuperclass
public abstract class TraceableEntity {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private com.tongkey.domain.SourceType sourceType = com.tongkey.domain.SourceType.NATIVE;

    /** 若为同步数据，记录来源数据源配置 ID */
    @Column(name = "source_id", length = 36)
    private String sourceId;

    /** 第三方系统中的原始主键/唯一标识（同步时做 upsert 匹配） */
    @Column(name = "external_key", length = 255)
    private String externalKey;

    /** JSON 扩展字段 */
    @Column(name = "extra_attrs", columnDefinition = "text")
    private String extraAttrs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 128, updatable = false)
    private String createdBy;

    @Column(name = "updated_by", length = 128)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public com.tongkey.domain.SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(com.tongkey.domain.SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public String getExternalKey() {
        return externalKey;
    }

    public void setExternalKey(String externalKey) {
        this.externalKey = externalKey;
    }

    public String getExtraAttrs() {
        return extraAttrs;
    }

    public void setExtraAttrs(String extraAttrs) {
        this.extraAttrs = extraAttrs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }
}
