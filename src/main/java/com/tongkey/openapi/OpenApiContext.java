package com.tongkey.openapi;

import com.tongkey.common.OperatorContext;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 开放 API 调用方上下文：由 {@link OpenApiAuthFilter} 在鉴权通过后填充。
 * <p>双通道：
 * <ul>
 *   <li>X-API-Key：以接入方配置的全量 scope 生效；</li>
 *   <li>OAuth2 Bearer JWT：以令牌内携带的授权 scope（可能窄于接入方全量 scope）生效，
 *       并携带授权用户信息。</li>
 * </ul>
 */
public final class OpenApiContext {

    private static final ThreadLocal<ClientEntity> CLIENT = new ThreadLocal<>();
    private static final ThreadLocal<String> TOKEN_SCOPES = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<String> CHANNEL = new ThreadLocal<>();

    public static final String ATTR_BODY = "tk.requestBody";
    public static final String CHANNEL_API_KEY = "api_key";
    public static final String CHANNEL_OAUTH2 = "oauth2";

    private OpenApiContext() {
    }

    /** X-API-Key 通道：接入方全量 scope。 */
    public static void set(ClientEntity client) {
        CLIENT.set(client);
        CHANNEL.set(CHANNEL_API_KEY);
        OperatorContext.set(client.getClientId(), "API:" + client.getClientId());
    }

    /** OAuth2 Bearer 通道：令牌 scope（空格分隔）+ 授权用户。 */
    public static void setOauth(ClientEntity client, String userId, String username, String tokenScopes) {
        CLIENT.set(client);
        USER_ID.set(userId);
        USERNAME.set(username);
        TOKEN_SCOPES.set(tokenScopes == null ? "" : tokenScopes);
        CHANNEL.set(CHANNEL_OAUTH2);
        OperatorContext.set(username != null ? username : client.getClientId(),
                "OAUTH2:" + client.getClientId());
    }

    public static ClientEntity current() {
        return CLIENT.get();
    }

    public static String channel() {
        return CHANNEL.get();
    }

    public static String currentUserId() {
        return USER_ID.get();
    }

    public static String currentUsername() {
        return USERNAME.get();
    }

    /** 当前通道实际生效的 scope 集合（JWT 可能是接入方全量 scope 的子集）。 */
    public static java.util.List<String> effectiveScopes() {
        ClientEntity c = CLIENT.get();
        if (c == null) {
            return java.util.List.of();
        }
        String raw = CHANNEL_OAUTH2.equals(CHANNEL.get())
                ? TOKEN_SCOPES.get()
                : c.getScopes() == null ? "" : c.getScopes().replace(',', ' ');
        java.util.List<String> out = new java.util.ArrayList<>();
        if (raw != null) {
            for (String s : raw.split("[\\s,]+")) {
                if (!s.isBlank()) {
                    out.add(s.trim());
                }
            }
        }
        return out;
    }

    public static void clear() {
        CLIENT.remove();
        TOKEN_SCOPES.remove();
        USER_ID.remove();
        USERNAME.remove();
        CHANNEL.remove();
        OperatorContext.clear();
    }

    /** 校验当前调用方是否具备指定 scope，如 user:read。 */
    public static void requireScope(String scope) {
        ClientEntity c = current();
        if (c == null) {
            throw new com.tongkey.common.ApiException(com.tongkey.common.ErrorCode.UNAUTHORIZED);
        }
        if (effectiveScopes().stream().anyMatch(s -> s.equalsIgnoreCase(scope))) {
            return;
        }
        throw new com.tongkey.common.ApiException(com.tongkey.common.ErrorCode.FORBIDDEN,
                "当前令牌未被授权该操作，缺少 scope: " + scope);
    }

    public static String cachedBody(HttpServletRequest request) {
        Object v = request.getAttribute(ATTR_BODY);
        return v == null ? "" : v.toString();
    }
}
