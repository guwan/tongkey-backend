package com.tongkey.openapi;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 开放 API 访问日志（规格文档 7.3）：调用方、接口、参数摘要、响应状态、耗时。
 */
@Entity
@Table(name = "tk_api_access_log", indexes = {
        @Index(name = "idx_apilog_client", columnList = "client_id, created_at"),
        @Index(name = "idx_apilog_created", columnList = "created_at")
})
public class ApiAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", length = 64)
    private String clientId;

    @Column(nullable = false, length = 8)
    private String method;

    @Column(nullable = false, length = 255)
    private String path;

    @Column(name = "param_summary", length = 1024)
    private String paramSummary;

    @Column(name = "http_status")
    private int httpStatus;

    @Column(name = "cost_ms")
    private long costMs;

    @Column(name = "remote_ip", length = 64)
    private String remoteIp;

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

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getParamSummary() {
        return paramSummary;
    }

    public void setParamSummary(String paramSummary) {
        this.paramSummary = paramSummary;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public long getCostMs() {
        return costMs;
    }

    public void setCostMs(long costMs) {
        this.costMs = costMs;
    }

    public String getRemoteIp() {
        return remoteIp;
    }

    public void setRemoteIp(String remoteIp) {
        this.remoteIp = remoteIp;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
