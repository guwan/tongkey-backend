package com.tongkey.oauth2;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OAuth2 授权端点的服务端渲染 HTML（登录页 / 同意页 / 错误页）。
 * 内联 CSS、无外部依赖；所有用户可控内容均经 HTML 转义，防 XSS。
 */
public final class OAuthPages {

    private OAuthPages() {
    }

    /** scope → 中文说明（同意页展示）。 */
    private static final Map<String, String> SCOPE_LABELS = new LinkedHashMap<>();

    static {
        SCOPE_LABELS.put("user:read", "读取用户信息");
        SCOPE_LABELS.put("user:write", "写入/更新用户信息");
        SCOPE_LABELS.put("role:read", "读取角色信息");
        SCOPE_LABELS.put("role:write", "写入/更新角色信息");
        SCOPE_LABELS.put("permission:read", "读取权限信息");
        SCOPE_LABELS.put("permission:write", "写入/更新权限信息");
        SCOPE_LABELS.put("user_role:write", "建立/解除用户与角色的关联");
        SCOPE_LABELS.put("role_permission:write", "建立/解除角色与权限的关联");
        SCOPE_LABELS.put("change:read", "读取变更日志");
        SCOPE_LABELS.put("sync:run", "触发数据同步");
        SCOPE_LABELS.put("oauth2:login", "使用 TongKey 账号登录授权");
    }

    public static String scopeLabel(String scope) {
        return SCOPE_LABELS.getOrDefault(scope, scope);
    }

    /** 登录页：未登录会话时展示，隐藏字段保留授权请求参数。 */
    public static String loginPage(String error, String clientName, String clientId,
                                   String responseType, String redirectUri, String scope, String state) {
        return page("登录授权", """
                <div class="card">
                  <div class="brand">TongKey 开放式授权中心</div>
                  <h1>登录并授权</h1>
                  <p class="sub">接入方「%s」（%s）请求访问你的 TongKey 账号，请先登录。</p>
                  %s
                  <form method="post" action="/oauth2/authorize/login">
                    %s
                    <label>用户名</label>
                    <input name="username" autocomplete="username" autofocus required>
                    <label>密码</label>
                    <input name="password" type="password" autocomplete="current-password" required>
                    <button type="submit" class="btn-primary">登录</button>
                  </form>
                  <p class="foot">授权页会话仅在本流程内有效，30 分钟无操作需重新登录。</p>
                </div>
                """.formatted(
                esc(clientName), esc(clientId),
                error == null ? "" : "<div class='error'>" + esc(error) + "</div>",
                hiddenFields(responseType, clientId, redirectUri, scope, state)));
    }

    /** 同意页：已登录会话时展示接入方申请的权限。 */
    public static String consentPage(String username, String displayName, String clientName, String clientId,
                                     String redirectUri, String scopes,
                                     String responseType, String state) {
        StringBuilder rows = new StringBuilder();
        for (String s : scopes.split("\\s+")) {
            if (s.isBlank()) {
                continue;
            }
            rows.append("<li><b>").append(esc(scopeLabel(s))).append("</b><code>").append(esc(s)).append("</code></li>");
        }
        String who = displayName != null && !displayName.isBlank() ? displayName + "（" + username + "）" : username;
        return page("授权确认", """
                <div class="card">
                  <div class="brand">TongKey 开放式授权中心</div>
                  <h1>授权确认</h1>
                  <p class="sub"><b>%s</b>，接入方「%s」（<code>%s</code>）申请以下权限：</p>
                  <ul class="scopes">%s</ul>
                  <p class="sub">授权后将跳转至：<code class="uri">%s</code></p>
                  <form method="post" action="/oauth2/authorize/consent" class="actions">
                    %s
                    <input type="hidden" name="decision" value="approve">
                    <button type="submit" class="btn-primary">同意授权</button>
                  </form>
                  <form method="post" action="/oauth2/authorize/consent" class="actions">
                    %s
                    <input type="hidden" name="decision" value="deny">
                    <button type="submit" class="btn-danger">拒绝</button>
                  </form>
                  <p class="foot">当前登录：%s · <a href="/oauth2/authorize/logout?%s">切换账号</a></p>
                </div>
                """.formatted(
                esc(who), esc(clientName), esc(clientId), rows.toString(), esc(redirectUri),
                hiddenFields(responseType, clientId, redirectUri, scopes, state),
                hiddenFields(responseType, clientId, redirectUri, scopes, state),
                esc(who), esc(buildQuery(responseType, clientId, redirectUri, scopes, state))));
    }

    /** 参数不可信/不可恢复时展示的错误页（绝不做重定向）。 */
    public static String errorPage(String error, String description) {
        return page("授权错误", """
                <div class="card">
                  <div class="brand">TongKey 开放式授权中心</div>
                  <h1>授权请求无效</h1>
                  <div class="error"><b>%s</b><div class="desc">%s</div></div>
                  <p class="foot">如你是接入方开发者，请核对 client_id、redirect_uri 与申请的 scope。本页面不会跳转，以防开放重定向。</p>
                </div>
                """.formatted(esc(error), esc(description)));
    }

    private static String hiddenFields(String responseType, String clientId, String redirectUri,
                                       String scope, String state) {
        return """
                <input type="hidden" name="response_type" value="%s">
                <input type="hidden" name="client_id" value="%s">
                <input type="hidden" name="redirect_uri" value="%s">
                <input type="hidden" name="scope" value="%s">
                <input type="hidden" name="state" value="%s">
                """.formatted(esc(responseType), esc(clientId), esc(redirectUri), esc(scope), esc(state));
    }

    private static String buildQuery(String responseType, String clientId, String redirectUri,
                                     String scope, String state) {
        StringBuilder sb = new StringBuilder();
        appendParam(sb, "response_type", responseType);
        appendParam(sb, "client_id", clientId);
        appendParam(sb, "redirect_uri", redirectUri);
        appendParam(sb, "scope", scope);
        appendParam(sb, "state", state);
        return sb.toString();
    }

    private static void appendParam(StringBuilder sb, String k, String v) {
        if (v == null) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append('&');
        }
        sb.append(k).append('=').append(java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8));
    }

    public static String esc(String v) {
        if (v == null) {
            return "";
        }
        return v.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String page(String title, String body) {
        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s · TongKey OAuth2</title>
                <style>
                  * { box-sizing: border-box; }
                  body { margin:0; min-height:100vh; display:flex; align-items:center; justify-content:center;
                         background:#f1f5f9; font-family:-apple-system,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif; color:#1e293b; }
                  .card { background:#fff; border:1px solid #e2e8f0; border-radius:12px; padding:32px;
                          width:420px; max-width:94vw; box-shadow:0 8px 30px rgba(15,23,42,.08); }
                  .brand { font-size:12px; color:#64748b; letter-spacing:.05em; margin-bottom:12px; }
                  h1 { font-size:20px; margin:0 0 12px; }
                  .sub { font-size:13px; color:#475569; line-height:1.7; margin:0 0 18px; word-break:break-all; }
                  label { display:block; font-size:13px; color:#334155; margin:12px 0 6px; }
                  input[type=text],input:not([type]) { width:100%%; padding:9px 11px; border:1px solid #cbd5e1;
                          border-radius:8px; font-size:14px; outline:none; }
                  input:focus { border-color:#2563eb; box-shadow:0 0 0 3px rgba(37,99,235,.15); }
                  .btn-primary,.btn-danger { width:100%%; margin-top:18px; padding:10px; border-radius:8px;
                          border:none; font-size:14px; cursor:pointer; }
                  .btn-primary { background:#2563eb; color:#fff; } .btn-primary:hover { background:#1d4ed8; }
                  .btn-danger { background:#fff; border:1px solid #fca5a5; color:#dc2626; margin-top:10px; }
                  .btn-danger:hover { background:#fef2f2; }
                  .error { background:#fef2f2; border:1px solid #fecaca; color:#b91c1c; padding:10px 12px;
                           border-radius:8px; font-size:13px; margin-bottom:14px; }
                  .error .desc { margin-top:4px; color:#991b1b; }
                  .scopes { list-style:none; padding:0; margin:0 0 18px; }
                  .scopes li { padding:8px 10px; border:1px solid #e2e8f0; border-radius:8px; margin-bottom:8px; font-size:13px; }
                  .scopes code { margin-left:8px; color:#7c3aed; font-size:11px; background:#f5f3ff; padding:1px 6px; border-radius:4px; }
                  code { font-family:ui-monospace,Consolas,monospace; }
                  code.uri { font-size:11px; color:#475569; }
                  .actions { margin:0; }
                  .foot { font-size:11px; color:#94a3b8; margin:14px 0 0; line-height:1.6; }
                  a { color:#2563eb; }
                </style>
                </head>
                <body>%s</body>
                </html>
                """.formatted(esc(title), body);
    }
}
