package com.tongkey.oauth2;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * OAuth 2.0 子系统配置（前缀 {@code tongkey.oauth2}）。
 *
 * @param jwtSecret            HS256 JWT 与授权页会话 Cookie 的签名密钥（至少 32 字符）
 * @param issuer               JWT iss 声明
 * @param accessTokenTtlSeconds access_token 有效期（默认 2 小时）
 * @param refreshTokenTtlDays  refresh_token 有效期（默认 30 天）
 * @param codeTtlSeconds       授权码有效期（默认 10 分钟）
 * @param sessionTtlSeconds    授权页登录会话 Cookie 有效期（默认 30 分钟）
 */
@Component
@ConfigurationProperties(prefix = "tongkey.oauth2")
public class OAuth2Properties {

    /** 开发环境兜底密钥，生产环境绝不允许使用。 */
    public static final String DEV_DEFAULT_SECRET = "tongkey-oauth2-dev-only-secret-do-not-use-in-prod";

    private String jwtSecret = DEV_DEFAULT_SECRET;
    private String issuer = "tongkey";
    private long accessTokenTtlSeconds = 7200;
    private long refreshTokenTtlDays = 30;
    private long codeTtlSeconds = 600;
    private long sessionTtlSeconds = 1800;

    private final Environment environment;

    public OAuth2Properties(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalStateException("tongkey.oauth2.jwt-secret 必须配置且长度至少 32 字符");
        }
        boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (isProd && DEV_DEFAULT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException("生产环境(prod)必须通过环境变量 TONGKEY_OAUTH_JWT_SECRET 或 application-secret.yml 配置 tongkey.oauth2.jwt-secret");
        }
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public long getRefreshTokenTtlDays() {
        return refreshTokenTtlDays;
    }

    public void setRefreshTokenTtlDays(long refreshTokenTtlDays) {
        this.refreshTokenTtlDays = refreshTokenTtlDays;
    }

    public long getCodeTtlSeconds() {
        return codeTtlSeconds;
    }

    public void setCodeTtlSeconds(long codeTtlSeconds) {
        this.codeTtlSeconds = codeTtlSeconds;
    }

    public long getSessionTtlSeconds() {
        return sessionTtlSeconds;
    }

    public void setSessionTtlSeconds(long sessionTtlSeconds) {
        this.sessionTtlSeconds = sessionTtlSeconds;
    }
}
