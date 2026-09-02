package com.tongkey.sync;

import com.tongkey.datasource.SyncMode;
import com.tongkey.domain.ConflictStrategy;
import com.tongkey.domain.EntityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * SQL 映射配置（规格文档 5.2）：每个数据源下可配置多个映射任务，
 * 分别对应拉取用户/角色/权限/用户角色关系/角色权限关系。
 */
@Entity
@Table(name = "tk_sync_mapping", indexes = @Index(name = "idx_mapping_ds", columnList = "data_source_id"))
public class SyncMapping {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Column(name = "data_source_id", nullable = false, length = 36)
    private String dataSourceId;

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_entity", nullable = false, length = 20)
    private EntityType targetEntity;

    /** 配置的查询 SQL，支持 :lastSyncTime 占位符用于增量同步 */
    @Column(name = "sql_text", nullable = false, columnDefinition = "text")
    private String sqlText;

    /** JSON：目标实体字段 → SQL 结果列 的映射 */
    @Column(name = "field_mapping", nullable = false, columnDefinition = "text")
    private String fieldMapping;

    @Enumerated(EnumType.STRING)
    @Column(name = "conflict_strategy", nullable = false, length = 24)
    private ConflictStrategy conflictStrategy = ConflictStrategy.SYNC_OVERRIDE;

    @Column(name = "batch_size")
    private int batchSize = 500;

    @Column(nullable = false)
    private boolean enabled = true;

    /** 增量同步水位：上次同步到的增量列最大值 */
    @Column(name = "last_sync_value", length = 128)
    private String lastSyncValue;

    /** 同步模式：FULL（全量）/ INCREMENTAL（增量），每个映射独立配置 */
    @Enumerated(EnumType.STRING)
    @Column(name = "sync_mode", nullable = false, length = 16)
    private SyncMode syncMode = SyncMode.FULL;

    /** 增量同步依据字段（仅增量模式使用），如 UPDATE_ON */
    @Column(name = "incremental_column", length = 128)
    private String incrementalColumn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    public String getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(String dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public EntityType getTargetEntity() {
        return targetEntity;
    }

    public void setTargetEntity(EntityType targetEntity) {
        this.targetEntity = targetEntity;
    }

    public String getSqlText() {
        return sqlText;
    }

    public void setSqlText(String sqlText) {
        this.sqlText = sqlText;
    }

    public String getFieldMapping() {
        return fieldMapping;
    }

    public void setFieldMapping(String fieldMapping) {
        this.fieldMapping = fieldMapping;
    }

    public ConflictStrategy getConflictStrategy() {
        return conflictStrategy;
    }

    public void setConflictStrategy(ConflictStrategy conflictStrategy) {
        this.conflictStrategy = conflictStrategy;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getLastSyncValue() {
        return lastSyncValue;
    }

    public void setLastSyncValue(String lastSyncValue) {
        this.lastSyncValue = lastSyncValue;
    }

    public SyncMode getSyncMode() {
        return syncMode;
    }

    public void setSyncMode(SyncMode syncMode) {
        this.syncMode = syncMode;
    }

    public String getIncrementalColumn() {
        return incrementalColumn;
    }

    public void setIncrementalColumn(String incrementalColumn) {
        this.incrementalColumn = incrementalColumn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
