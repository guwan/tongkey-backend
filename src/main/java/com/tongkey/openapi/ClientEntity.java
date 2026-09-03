package com.tongkey.openapi;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * 开放 API 接入方（规格文档 7.3）：每个第三方系统分配 client_id + API Key，
 * 支持接口级权限（scopes）、按 Client 限流（qps）、可选请求签名校验。
 */
@Entity
@Table(name = "tk_client", uniqueConstraints = {
        @UniqueConstraint(name = "uk_client_id", columnNames = "client_id"),
        @UniqueConstraint(name = "uk_client_api_key", columnNames = "api_key")
})
public class ClientEntity {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(nullable = false, length = 128)
    private String name;

    /** AES-GCM 加密存储 */
    @Column(name = "client_secret", nullable = false, length = 1024)
    private String clientSecret;

    @Column(name = "api_key", nullable = false, length = 64)
    private String apiKey;

    /** 授权范围，逗号分隔：user:read,user:write,role:read,role:write,permission:read,permission:write,user_role:write,role_permission:write,change:read,sync:run */
    @Column(nullable = false, length = 1024)
    private String scopes = "user:read,role:read,permission:read";

    /** 每秒请求上限（令牌桶） */
    @Column(name = "qps_limit")
    private int qpsLimit = 50;

    /** 是否强制 HMAC-SHA256 签名校验（防重放） */
    @Column(name = "require_signature")
    private boolean requireSignature;

    @Column(nullable = false)
    private boolean enabled = true;

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

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public int getQpsLimit() {
        return qpsLimit;
    }

    public void setQpsLimit(int qpsLimit) {
        this.qpsLimit = qpsLimit;
    }

    public boolean isRequireSignature() {
        return requireSignature;
    }

    public void setRequireSignature(boolean requireSignature) {
        this.requireSignature = requireSignature;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
