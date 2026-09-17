package com.tongkey.oauth2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongkey.common.CryptoUtil;
import com.tongkey.openapi.ClientEntity;
import com.tongkey.openapi.ClientRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OAuth2 令牌端点（RFC 6749 §3.2、§4.1.3、§5、§6）。
 * <pre>
 *   POST /oauth2/token
 *   Content-Type: application/x-www-form-urlencoded | application/json
 *   Authorization: Basic base64(urlencode(client_id):urlencode(client_secret))  （或放入 body）
 * </pre>
 * 成功返回 RFC §5.1 原生 JSON，绝不包裹 TongKey 统一响应体；
 * 失败返回 RFC §5.2 {error, error_description}。
 */
@Tag(name = "OAuth2-令牌端点", description = "RFC 6749：授权码换令牌 / 刷新令牌；响应为 RFC 原生 JSON，非 TongKey 统一包装")
@RestController
@RequestMapping("/oauth2")
public class OAuthTokenController {

    private static final String REALM = "TongKey";

    private final ClientRepository clientRepository;
    private final CryptoUtil crypto;
    private final OAuthTokenService tokenService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OAuthTokenController(ClientRepository clientRepository,
                                CryptoUtil crypto,
                                OAuthTokenService tokenService) {
        this.clientRepository = clientRepository;
        this.crypto = crypto;
        this.tokenService = tokenService;
    }

    @Operation(summary = "获取/刷新令牌",
            description = "grant_type=authorization_code：参数 code + redirect_uri；grant_type=refresh_token：参数 refresh_token。"
                    + "客户端凭证用 Authorization: Basic base64(client_id:client_secret) 或 body 字段传递。"
                    + "成功 200 返回 {access_token, token_type, expires_in, scope, refresh_token}；"
                    + "失败 400/401 返回 RFC §5.2 {error, error_description}（注意：不走 {code,message,data} 统一包装）")
    @PostMapping(value = "/token", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> token(HttpServletRequest request) throws Exception {
        Map<String, String> params = readParams(request);
        String grantType = params.get("grant_type");
        if (grantType == null || grantType.isBlank()) {
            throw new OAuthErrorException(400, "invalid_request", "缺少 grant_type 参数");
        }
        ClientEntity client = authenticateClient(request, params);
        OAuthTokenService.IssuedTokens tokens = switch (grantType) {
            case "authorization_code" -> tokenService.exchangeCode(
                    params.get("code"), client.getClientId(), params.get("redirect_uri"));
            case "refresh_token" -> tokenService.refresh(
                    params.get("refresh_token"), client.getClientId());
            default -> throw new OAuthErrorException(400, "unsupported_grant_type",
                    "仅支持 authorization_code 与 refresh_token 两种 grant_type");
        };
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", tokens.accessToken());
        body.put("token_type", "Bearer");
        body.put("expires_in", tokens.expiresIn());
        body.put("scope", tokens.scope());
        body.put("refresh_token", tokens.refreshToken());
        return body;
    }

    /** RFC §3.2.1：优先 HTTP Basic，其次 body 中的 client_id/client_secret；必须恰好一种认证来源。 */
    private ClientEntity authenticateClient(HttpServletRequest request, Map<String, String> params) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        String basicId = null;
        String basicSecret = null;
        boolean basicPresent = header != null && header.regionMatches(true, 0, "Basic ", 0, 6);
        if (basicPresent) {
            String decoded;
            try {
                decoded = new String(Base64.getDecoder().decode(header.substring(6).trim()),
                        StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                throw new OAuthErrorException(401, "invalid_client", "Authorization 头不是合法的 Basic 认证");
            }
            int colon = decoded.indexOf(':');
            if (colon <= 0) {
                // 畸形 Basic 凭证必须直接拒绝，不能静默降级到 body 凭证
                throw new OAuthErrorException(401, "invalid_client", "Basic 认证格式错误，应为 base64(client_id:client_secret)");
            }
            basicId = decoded.substring(0, colon);
            basicSecret = decoded.substring(colon + 1);
        }
        String bodyId = params.get("client_id");
        String bodySecret = params.get("client_secret");
        String clientId;
        String clientSecret;
        if (basicId != null) {
            if (bodyId != null && !bodyId.isBlank()) {
                throw new OAuthErrorException(400, "invalid_request",
                        "client 凭证只能通过 Authorization Basic 或 body 其中一种方式传递");
            }
            clientId = basicId;
            clientSecret = basicSecret;
        } else {
            clientId = bodyId;
            clientSecret = bodySecret;
        }
        if (clientId == null || clientId.isBlank()) {
            throw new OAuthErrorException(401, "invalid_client", "缺少 client_id 凭证");
        }
        ClientEntity client = clientRepository.findByClientId(clientId).orElseThrow(
                () -> new OAuthErrorException(401, "invalid_client", "client_id 或 client_secret 错误"));
        if (!client.isEnabled()) {
            throw new OAuthErrorException(401, "invalid_client", "接入方已停用");
        }
        if (!OAuthClientSupport.hasScope(client, OAuth2Scopes.OAUTH2_LOGIN)) {
            throw new OAuthErrorException(400, "unauthorized_client", "该接入方未开通 oauth2:login 权限");
        }
        if (clientSecret == null || !constantTimeEquals(crypto.decrypt(client.getClientSecret()), clientSecret)) {
            throw new OAuthErrorException(401, "invalid_client", "client_id 或 client_secret 错误");
        }
        return client;
    }

    private boolean constantTimeEquals(String expected, String provided) {
        if (expected == null || provided == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 同时支持 application/x-www-form-urlencoded 与 application/json（JSON 方便 AI/脚本对接）。
     * 只解析请求体本身，<b>不合并 URL query string</b>，避免 client_secret 经 URL/代理日志泄露。
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> readParams(HttpServletRequest request) throws Exception {
        Map<String, String> out = new HashMap<>();
        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().contains("application/json")) {
            Map<String, Object> json = objectMapper.readValue(request.getInputStream(), Map.class);
            json.forEach((k, v) -> {
                if (v != null) {
                    out.put(k, String.valueOf(v));
                }
            });
            return out;
        }
        String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!body.isBlank()) {
            for (String pair : body.split("&")) {
                int eq = pair.indexOf('=');
                String key = eq >= 0 ? pair.substring(0, eq) : pair;
                String val = eq >= 0 ? pair.substring(eq + 1) : "";
                if (!key.isEmpty()) {
                    out.put(java.net.URLDecoder.decode(key, StandardCharsets.UTF_8),
                            java.net.URLDecoder.decode(val, StandardCharsets.UTF_8));
                }
            }
        }
        return out;
    }

    /** 本控制器错误统一走 RFC §5.2，不被全局 ApiResponse 包装。 */
    @ExceptionHandler(OAuthErrorException.class)
    public ResponseEntity<Map<String, Object>> handleOAuthError(OAuthErrorException ex) {
        return rfcError(ex.getHttpStatus(), ex.getErrorCode(), ex.getMessage(), ex.getHttpStatus() == 401);
    }

    /**
     * 兜底：请求体解析失败（畸形 JSON/表单）等参数类异常一律 400 invalid_request；
     * 其余未预期异常 500 server_error。保证 token 端点在任何情况下都不退回统一包装响应。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAny(Exception ex) {
        if (ex instanceof com.fasterxml.jackson.core.JsonProcessingException
                || ex instanceof IllegalArgumentException) {
            return rfcError(400, "invalid_request", "请求体解析失败：" + ex.getMessage(), false);
        }
        return rfcError(500, "server_error", "令牌服务内部错误", false);
    }

    private ResponseEntity<Map<String, Object>> rfcError(int status, String error, String description,
                                                          boolean basicChallenge) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("error_description", description);
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");
        headers.setPragma("no-cache");
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (basicChallenge) {
            headers.add(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"" + REALM + "\"");
        }
        return new ResponseEntity<>(body, headers, status);
    }
}
