package com.tongkey.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 推送目标配置（规格文档 6.1）：第三方 Webhook 接收端。
 */
@Entity
@Table(name = "tk_push_target")
public class PushTarget {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "endpoint_url", nullable = false, length = 1024)
    private String endpointUrl;

    @Column(name = "http_method", nullable = false, length = 8)
    private String httpMethod = "POST";

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type", nullable = false, length = 20)
    private PushAuthType authType = PushAuthType.NONE;

    /** 鉴权密钥等（AES-GCM 加密存储，JSON：basic={username,password} / bearer={token} / hmac={secretKey,headerName}） */
    @Column(name = "auth_config", length = 2048)
    private String authConfig;

    /** 触发时机，逗号分隔：ON_INIT,ON_CREATE,ON_UPDATE,ON_DELETE */
    @Column(name = "trigger_events", nullable = false, length = 128)
    private String triggerEvents = "ON_CREATE,ON_UPDATE,ON_DELETE";

    /** 推送实体范围，逗号分隔：USER,ROLE,PERMISSION,USER_ROLE,ROLE_PERMISSION */
    @Column(name = "entity_scope", nullable = false, length = 255)
    private String entityScope = "USER,ROLE,PERMISSION";

    /** 推送报文字段映射模板（JSON：第三方字段名 → 本系统快照字段名），为空则发送标准报文 */
    @Column(name = "payload_template", columnDefinition = "text")
    private String payloadTemplate;

    @Column(name = "retry_max")
    private int retryMax = 3;

    /** 首次重试间隔秒数，后续按指数退避翻倍 */
    @Column(name = "retry_interval_seconds")
    private int retryIntervalSeconds = 30;

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

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public PushAuthType getAuthType() {
        return authType;
    }

    public void setAuthType(PushAuthType authType) {
        this.authType = authType;
    }

    public String getAuthConfig() {
        return authConfig;
    }

    public void setAuthConfig(String authConfig) {
        this.authConfig = authConfig;
    }

    public String getTriggerEvents() {
        return triggerEvents;
    }

    public void setTriggerEvents(String triggerEvents) {
        this.triggerEvents = triggerEvents;
    }

    public String getEntityScope() {
        return entityScope;
    }

    public void setEntityScope(String entityScope) {
        this.entityScope = entityScope;
    }

    public String getPayloadTemplate() {
        return payloadTemplate;
    }

    public void setPayloadTemplate(String payloadTemplate) {
        this.payloadTemplate = payloadTemplate;
    }

    public int getRetryMax() {
        return retryMax;
    }

    public void setRetryMax(int retryMax) {
        this.retryMax = retryMax;
    }

    public int getRetryIntervalSeconds() {
        return retryIntervalSeconds;
    }

    public void setRetryIntervalSeconds(int retryIntervalSeconds) {
        this.retryIntervalSeconds = retryIntervalSeconds;
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
