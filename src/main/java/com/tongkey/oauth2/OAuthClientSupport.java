package com.tongkey.oauth2;

import com.tongkey.openapi.ClientEntity;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 接入方 OAuth 能力相关的纯函数：scope 解析、重定向 URI 白名单匹配。
 */
public final class OAuthClientSupport {

    private OAuthClientSupport() {
    }

    public static Set<String> scopeSet(String csv) {
        Set<String> out = new LinkedHashSet<>();
        if (csv == null) {
            return out;
        }
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /** 解析空格分隔的 scope 串（OAuth2 线上格式）。 */
    public static Set<String> parseGrantedScopes(String spaceSeparated) {
        Set<String> out = new LinkedHashSet<>();
        if (spaceSeparated == null) {
            return out;
        }
        for (String s : spaceSeparated.split("\\s+")) {
            if (!s.isBlank()) {
                out.add(s.trim());
            }
        }
        return out;
    }

    public static boolean hasScope(ClientEntity client, String scope) {
        return scopeSet(client.getScopes()).contains(scope);
    }

    /** 解析接入方配置的回调地址白名单：支持换行、逗号、空白分隔。 */
    public static List<String> redirectUriList(String raw) {
        if (raw == null) {
            return List.of();
        }
        return Arrays.stream(raw.split("[\\s,]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * 重定向 URI 必须与白名单中的某一条<b>精确相等</b>（防开放重定向，RFC 6749 §3.1.2.3）。
     */
    public static boolean redirectUriAllowed(ClientEntity client, String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) {
            return false;
        }
        return redirectUriList(client.getRedirectUris()).contains(redirectUri.trim());
    }
}
