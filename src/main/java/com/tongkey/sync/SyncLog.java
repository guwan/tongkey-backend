package com.tongkey.sync;

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
 * 同步执行日志（规格文档 5.3 第 5 步）：成功/失败条数、错误详情、耗时。
 */
@Entity
@Table(name = "tk_sync_log", indexes = {
        @Index(name = "idx_synclog_mapping", columnList = "mapping_id, started_at"),
        @Index(name = "idx_synclog_started", columnList = "started_at")
})
public class SyncLog {

    public enum SyncStatus {
        RUNNING, SUCCESS, FAILED
    }

    public enum SyncTrigger {
        MANUAL, SCHEDULED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mapping_id", nullable = false, length = 36)
    private String mappingId;

    @Column(name = "data_source_id", nullable = false, length = 36)
    private String dataSourceId;

    @Column(name = "mapping_name", length = 128)
    private String mappingName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SyncStatus status = SyncStatus.RUNNING;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_trigger", nullable = false, length = 16)
    private SyncTrigger trigger;

    @Column(name = "inserted_count")
    private long insertedCount;

    @Column(name = "updated_count")
    private long updatedCount;

    @Column(name = "skipped_count")
    private long skippedCount;

    @Column(name = "failed_count")
    private long failedCount;

    @Column(name = "error_detail", columnDefinition = "text")
    private String errorDetail;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @PrePersist
    void onCreate() {
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getMappingId() {
        return mappingId;
    }

    public void setMappingId(String mappingId) {
        this.mappingId = mappingId;
    }

    public String getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(String dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getMappingName() {
        return mappingName;
    }

    public void setMappingName(String mappingName) {
        this.mappingName = mappingName;
    }

    public SyncStatus getStatus() {
        return status;
    }

    public void setStatus(SyncStatus status) {
        this.status = status;
    }

    public SyncTrigger getTrigger() {
        return trigger;
    }

    public void setTrigger(SyncTrigger trigger) {
        this.trigger = trigger;
    }

    public long getInsertedCount() {
        return insertedCount;
    }

    public void setInsertedCount(long insertedCount) {
        this.insertedCount = insertedCount;
    }

    public long getUpdatedCount() {
        return updatedCount;
    }

    public void setUpdatedCount(long updatedCount) {
        this.updatedCount = updatedCount;
    }

    public long getSkippedCount() {
        return skippedCount;
    }

    public void setSkippedCount(long skippedCount) {
        this.skippedCount = skippedCount;
    }

    public long getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(long failedCount) {
        this.failedCount = failedCount;
    }

    public String getErrorDetail() {
        return errorDetail;
    }

    public void setErrorDetail(String errorDetail) {
        this.errorDetail = errorDetail;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }
}
