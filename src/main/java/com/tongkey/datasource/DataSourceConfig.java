package com.tongkey.datasource;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * 第三方 SQL 数据源配置（规格文档 5.1）。密码加密存储，页面展示脱敏。
 */
@Entity
@Table(name = "tk_datasource", uniqueConstraints = @UniqueConstraint(name = "uk_datasource_name", columnNames = "name"))
public class DataSourceConfig {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "db_type", nullable = false, length = 16)
    private DbType dbType;

    @Column(name = "jdbc_url", nullable = false, length = 1024)
    private String jdbcUrl;

    @Column(length = 128)
    private String username;

    /** AES-GCM 加密存储 */
    @Column(length = 1024)
    private String password;

    @Column(nullable = false)
    private boolean enabled = true;

    /** 定时拉取 cron（Spring 6 位），留空表示仅手动触发 */
    @Column(name = "schedule_cron", length = 64)
    private String scheduleCron;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_mode", nullable = false, length = 16)
    private SyncMode syncMode = SyncMode.FULL;

    /** 增量同步依据字段，如 update_time */
    @Column(name = "incremental_column", length = 128)
    private String incrementalColumn;

    /** 连接超时（秒） */
    @Column(name = "connect_timeout_seconds")
    private int connectTimeoutSeconds = 10;

    @Column(length = 1024)
    private String notes;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DbType getDbType() {
        return dbType;
    }

    public void setDbType(DbType dbType) {
        this.dbType = dbType;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public void setJdbcUrl(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getScheduleCron() {
        return scheduleCron;
    }

    public void setScheduleCron(String scheduleCron) {
        this.scheduleCron = scheduleCron;
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

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
