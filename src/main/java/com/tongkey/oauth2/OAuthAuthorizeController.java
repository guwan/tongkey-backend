package com.tongkey.oauth2;

import com.tongkey.common.CryptoUtil;
import com.tongkey.domain.EntityStatus;
import com.tongkey.domain.entity.UserEntity;
import com.tongkey.domain.repository.UserRepository;
import com.tongkey.openapi.ClientEntity;
import com.tongkey.openapi.ClientRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * OAuth2 授权端点（RFC 6749 §4.1）：
 * <pre>
 *   GET  /oauth2/authorize          授权入口（登录页 / 同意页）
 *   POST /oauth2/authorize/login    用户登录（tk_user 用户名+密码），发 HttpOnly 会话 Cookie
 *   POST /oauth2/authorize/consent   用户同意/拒绝
 *   GET  /oauth2/authorize/logout    清除会话
 * </pre>
 * redirect_uri 任何不信任的情形一律展示错误页，绝不重定向（防开放重定向）。
 */
@Tag(name = "OAuth2-授权端点（浏览器交互）",
        description = "RFC 6749 §4.1：返回 HTML 登录/同意页与 302 跳转，非 JSON 接口，供浏览器访问")
@Controller
@RequestMapping("/oauth2")
public class OAuthAuthorizeController {

    /** 授权页登录会话 Cookie 名。 */
    public static final String SESSION_COOKIE = "tk_oauth_session";

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final CryptoUtil crypto;
    private final JwtTokenService jwt;
    private final OAuthTokenService tokenService;

    public OAuthAuthorizeController(ClientRepository clientRepository,
                                    UserRepository userRepository,
                                    CryptoUtil crypto,
                                    JwtTokenService jwt,
                                    OAuthTokenService tokenService) {
        this.clientRepository = clientRepository;
        this.userRepository = userRepository;
        this.crypto = crypto;
        this.jwt = jwt;
        this.tokenService = tokenService;
    }

    /** 校验通过的授权请求上下文。 */
    private record AuthzContext(ClientEntity client, String redirectUri, String scope, String state) {
    }

    @Operation(summary = "授权入口（浏览器）",
            description = "response_type 固定 code；redirect_uri 必须与接入方白名单精确一致；未登录渲染登录页，已登录渲染同意页")
    @GetMapping("/authorize")
    public void authorize(@RequestParam(value = "response_type", required = false) String responseType,
                          @RequestParam(value = "client_id", required = false) String clientId,
                          @RequestParam(value = "redirect_uri", required = false) String redirectUri,
                          @RequestParam(value = "scope", required = false) String scope,
                          @RequestParam(value = "state", required = false) String state,
                          HttpServletRequest request,
                          HttpServletResponse response) throws IOException {
        AuthzContext ctx = validate(responseType, clientId, redirectUri, scope);
        UserEntity user = currentUser(request).orElse(null);
        if (user == null) {
            writeHtml(response, 200, OAuthPages.loginPage(null, ctx.client().getName(), ctx.client().getClientId(),
                    "code", ctx.redirectUri(), ctx.scope(), state));
            return;
        }
        writeHtml(response, 200, OAuthPages.consentPage(user.getUsername(), user.getDisplayName(),
                ctx.client().getName(), ctx.client().getClientId(), ctx.redirectUri(), ctx.scope(),
                "code", state));
    }

    @Operation(summary = "授权页用户登录（表单）",
            description = "校验 tk_user 用户名+密码；成功 Set-Cookie tk_oauth_session（HttpOnly, SameSite=Lax）并 302 回授权页；失败 401 重渲染登录页")
    @PostMapping("/authorize/login")
    public void login(@RequestParam(value = "username", required = false) String username,
                      @RequestParam(value = "password", required = false) String password,
                      @RequestParam(value = "response_type", required = false) String responseType,
                      @RequestParam(value = "client_id", required = false) String clientId,
                      @RequestParam(value = "redirect_uri", required = false) String redirectUri,
                      @RequestParam(value = "scope", required = false) String scope,
                      @RequestParam(value = "state", required = false) String state,
                      HttpServletRequest request,
                      HttpServletResponse response) throws IOException {
        AuthzContext ctx = validate(responseType, clientId, redirectUri, scope);
        String loginError = verifyCredentials(username, password);
        if (loginError != null) {
            writeHtml(response, 401, OAuthPages.loginPage(loginError, ctx.client().getName(),
                    ctx.client().getClientId(), "code", ctx.redirectUri(), ctx.scope(), state));
            return;
        }
        UserEntity user = userRepository.findByUsername(username.trim())
                .filter(u -> u.getStatus() == EntityStatus.ENABLED)
                .orElseThrow(); // verifyCredentials 已确保存在且启用
        response.addHeader("Set-Cookie", buildSessionCookie(jwt.issueSessionCookie(user.getId()), request,
                (int) jwt.getSessionTtlSeconds()));
        response.sendRedirect(request.getContextPath() + "/oauth2/authorize?" + rebuildQuery(
                ctx.client().getClientId(), ctx.redirectUri(), ctx.scope(), state));
    }

    @Operation(summary = "用户同意/拒绝授权（表单）",
            description = "同意：签发一次性授权码并 302 redirect_uri?code=&state=；拒绝：302 redirect_uri?error=access_denied&state=")
    @PostMapping("/authorize/consent")
    public void consent(@RequestParam(value = "decision", required = false) String decision,
                        @RequestParam(value = "response_type", required = false) String responseType,
                        @RequestParam(value = "client_id", required = false) String clientId,
                        @RequestParam(value = "redirect_uri", required = false) String redirectUri,
                        @RequestParam(value = "scope", required = false) String scope,
                        @RequestParam(value = "state", required = false) String state,
                        HttpServletRequest request,
                        HttpServletResponse response) throws IOException {
        AuthzContext ctx = validate(responseType, clientId, redirectUri, scope);
        UserEntity user = currentUser(request).orElse(null);
        if (user == null) {
            // 会话过期：回到登录页
            response.sendRedirect(request.getContextPath() + "/oauth2/authorize?" + rebuildQuery(
                    ctx.client().getClientId(), ctx.redirectUri(), ctx.scope(), state));
            return;
        }
        String sep = ctx.redirectUri().contains("?") ? "&" : "?";
        if (!"approve".equals(decision)) {
            response.sendRedirect(ctx.redirectUri() + sep + "error=access_denied"
                    + (state == null ? "" : "&state=" + enc(state)));
            return;
        }
        String rawCode = tokenService.createAuthorizationCode(ctx.client(), user, ctx.scope(), ctx.redirectUri());
        response.sendRedirect(ctx.redirectUri() + sep + "code=" + enc(rawCode)
                + (state == null ? "" : "&state=" + enc(state)));
    }

    @GetMapping("/authorize/logout")
    public void logout(@RequestParam(value = "response_type", required = false) String responseType,
                       @RequestParam(value = "client_id", required = false) String clientId,
                       @RequestParam(value = "redirect_uri", required = false) String redirectUri,
                       @RequestParam(value = "scope", required = false) String scope,
                       @RequestParam(value = "state", required = false) String state,
                       HttpServletRequest request,
                       HttpServletResponse response) throws IOException {
        AuthzContext ctx = validate(responseType, clientId, redirectUri, scope);
        response.addHeader("Set-Cookie", buildSessionCookie("", request, 0));
        response.sendRedirect(request.getContextPath() + "/oauth2/authorize?" + rebuildQuery(
                ctx.client().getClientId(), ctx.redirectUri(), ctx.scope(), state));
    }

    // ── 校验与认证 ───────────────────────────────────────────────

    /** 校验授权请求；任何参数错误抛异常由 @ExceptionHandler 渲染错误页（不做重定向）。 */
    private AuthzContext validate(String responseType, String clientId, String redirectUri, String scope) {
        if (!"code".equals(responseType)) {
            throw new AuthorizePageException("unsupported_response_type",
                    "仅支持 response_type=code（授权码模式）。");
        }
        if (clientId == null || clientId.isBlank()) {
            throw new AuthorizePageException("invalid_request", "缺少 client_id 参数。");
        }
        ClientEntity client = clientRepository.findByClientId(clientId.trim()).orElse(null);
        if (client == null) {
            throw new AuthorizePageException("invalid_client", "client_id 不存在，请联系 TongKey 管理员。");
        }
        if (!client.isEnabled()) {
            throw new AuthorizePageException("access_denied", "该接入方已被停用。");
        }
        if (!OAuthClientSupport.hasScope(client, OAuth2Scopes.OAUTH2_LOGIN)) {
            throw new AuthorizePageException("unauthorized_client",
                    "该接入方未开通 oauth2:login 权限，不能发起 OAuth2 登录。");
        }
        if (!OAuthClientSupport.redirectUriAllowed(client, redirectUri)) {
            throw new AuthorizePageException("invalid_request",
                    "redirect_uri 不在接入方登记的白名单中，必须与配置的回调地址完全一致。");
        }
        Set<String> clientScopes = OAuthClientSupport.scopeSet(client.getScopes());
        Set<String> requested = new LinkedHashSet<>();
        if (scope != null && !scope.isBlank()) {
            for (String s : scope.trim().split("\\s+")) {
                if (!clientScopes.contains(s)) {
                    throw new AuthorizePageException("invalid_scope",
                            "scope「" + s + "」超出接入方已授权范围。");
                }
                requested.add(s);
            }
        } else {
            requested.addAll(clientScopes);
        }
        // oauth2:login 是"能否走 OAuth 流程"的能力开关，不作为资源权限写进令牌 scope
        requested.remove(OAuth2Scopes.OAUTH2_LOGIN);
        return new AuthzContext(client, redirectUri.trim(), String.join(" ", requested), null);
    }

    private static final String DUMMY_STORED_PASSWORD = "tk-dummy-password-for-constant-time-compare-only";

    /** 校验用户名+密码：成功返回 null，失败返回统一错误文案（不暴露用户是否存在/是否设密码）。 */
    private String verifyCredentials(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return "用户名或密码错误";
        }
        Optional<UserEntity> found = userRepository.findByUsername(username.trim());
        if (found.isEmpty() || found.get().getStatus() != EntityStatus.ENABLED) {
            // 账号不存在/停用时也做一次等量级比较，消除可枚举账号的时序差异
            constantTimePasswordCompare(DUMMY_STORED_PASSWORD, password);
            return "用户名或密码错误";
        }
        UserEntity user = found.get();
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            constantTimePasswordCompare(DUMMY_STORED_PASSWORD, password);
            return "用户名或密码错误";
        }
        try {
            if (!constantTimePasswordCompare(crypto.decrypt(user.getPassword()), password)) {
                return "用户名或密码错误";
            }
        } catch (Exception e) {
            return "用户名或密码错误";
        }
        return null;
    }

    private boolean constantTimePasswordCompare(String stored, String raw) {
        return MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.UTF_8), raw.getBytes(StandardCharsets.UTF_8));
    }

    private Optional<UserEntity> currentUser(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        for (Cookie c : request.getCookies()) {
            if (SESSION_COOKIE.equals(c.getName())) {
                String userId = jwt.verifySessionCookie(c.getValue());
                if (userId == null) {
                    return Optional.empty();
                }
                return userRepository.findById(userId)
                        .filter(u -> u.getStatus() == EntityStatus.ENABLED);
            }
        }
        return Optional.empty();
    }

    // ── 工具 ────────────────────────────────────────────────────

    private String rebuildQuery(String clientId, String redirectUri, String scope, String state) {
        StringBuilder sb = new StringBuilder("response_type=code");
        sb.append("&client_id=").append(enc(clientId));
        sb.append("&redirect_uri=").append(enc(redirectUri));
        if (scope != null && !scope.isBlank()) {
            sb.append("&scope=").append(enc(scope));
        }
        if (state != null) {
            sb.append("&state=").append(enc(state));
        }
        return sb.toString();
    }

    private String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    private String buildSessionCookie(String value, HttpServletRequest request, int maxAge) {
        boolean secure = request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        StringBuilder sb = new StringBuilder(SESSION_COOKIE).append('=').append(value)
                .append("; Path=/; HttpOnly; SameSite=Lax; Max-Age=").append(maxAge);
        if (secure) {
            sb.append("; Secure");
        }
        return sb.toString();
    }

    private void writeHtml(HttpServletResponse response, int status, String html) throws IOException {
        response.setStatus(status);
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write(html);
    }

    /** 参数不可信错误：渲染错误页（绝不重定向）。 */
    private static class AuthorizePageException extends RuntimeException {
        final String errorCode;

        AuthorizePageException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }
    }

    @ExceptionHandler(AuthorizePageException.class)
    public void handlePageError(AuthorizePageException ex, HttpServletResponse response) throws IOException {
        writeHtml(response, 400, OAuthPages.errorPage(ex.errorCode, ex.getMessage()));
    }
}
