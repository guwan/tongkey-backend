package com.tongkey.oauth2;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * OAuth2 授权码（RFC 6749 §4.1）：一次性、短有效期，仅以 SHA-256 哈希落库。
 */
@Entity
@Table(name = "tk_oauth_code", indexes = {
        @Index(name = "idx_oauth_code_hash", columnList = "code_hash", unique = true),
        @Index(name = "idx_oauth_code_client", columnList = "client_id, expires_at"),
})
public class OAuthCodeEntity {

    @Id
    @Column(length = 36)
    private String id = UUID.randomUUID().toString();

    /** 授权码的 SHA-256 哈希（明文绝不落库） */
    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(nullable = false, length = 128)
    private String username;

    /** 实际授予的 scope（空格分隔） */
    @Column(nullable = false, length = 1024)
    private String scopes;

    @Column(name = "redirect_uri", nullable = false, length = 1024)
    private String redirectUri;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String getId() {
        return id;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
