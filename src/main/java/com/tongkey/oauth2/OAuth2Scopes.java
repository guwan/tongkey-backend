package com.tongkey.oauth2;

/**
 * OAuth2 相关 scope 常量。
 */
public final class OAuth2Scopes {

    /** 允许该接入方使用 OAuth2 授权码流程登录（/oauth2/authorize、/oauth2/token）。 */
    public static final String OAUTH2_LOGIN = "oauth2:login";

    private OAuth2Scopes() {
    }
}
