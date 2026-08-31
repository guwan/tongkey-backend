package com.tongkey.openapi;

import com.tongkey.common.OperatorContext;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 开放 API 调用方上下文：由 {@link OpenApiAuthFilter} 在鉴权通过后填充。
 */
public final class OpenApiContext {

    private static final ThreadLocal<ClientEntity> CLIENT = new ThreadLocal<>();

    public static final String ATTR_BODY = "tk.requestBody";

    private OpenApiContext() {
    }

    public static void set(ClientEntity client) {
        CLIENT.set(client);
        OperatorContext.set(client.getClientId(), "API:" + client.getClientId());
    }

    public static ClientEntity current() {
        return CLIENT.get();
    }

    public static void clear() {
        CLIENT.remove();
        OperatorContext.clear();
    }

    /** 校验当前调用方是否具备指定 scope，如 user:read。 */
    public static void requireScope(String scope) {
        ClientEntity c = current();
        if (c == null) {
            throw new com.tongkey.common.ApiException(com.tongkey.common.ErrorCode.UNAUTHORIZED);
        }
        String scopes = c.getScopes() == null ? "" : c.getScopes();
        for (String s : scopes.split(",")) {
            if (s.trim().equalsIgnoreCase(scope)) {
                return;
            }
        }
        throw new com.tongkey.common.ApiException(com.tongkey.common.ErrorCode.FORBIDDEN,
                "当前 Client 未被授权该操作，缺少 scope: " + scope);
    }

    public static String cachedBody(HttpServletRequest request) {
        Object v = request.getAttribute(ATTR_BODY);
        return v == null ? "" : v.toString();
    }
}
