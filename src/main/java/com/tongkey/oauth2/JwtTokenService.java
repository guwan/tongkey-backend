package com.tongkey.oauth2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 零依赖的 OAuth2 令牌组件：
 * <ul>
 *   <li>HS256 JWT access_token 签发与验签（RFC 7519 子集）；</li>
 *   <li>授权页登录会话 Cookie 的 HMAC 签名/验签；</li>
 *   <li>授权码/刷新令牌的 SHA-256 哈希与不透明随机串生成。</li>
 * </ul>
 * 所有签名比较使用常量时间比较，防止时序攻击。
 */
@Component
public class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64URL_DEC = Base64.getUrlDecoder();

    private final OAuth2Properties props;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();

    public JwtTokenService(OAuth2Properties props) {
        this.props = props;
    }

    /** 验签通过后的 access_token 声明。 */
    public record AccessTokenClaims(String jti, String sub, String username, String clientId,
                                    String scope, long iat, long exp) {
    }

    /** 签发 HS256 JWT access_token。scope 为实际授予的 scope（空格分隔）。 */
    public String issueAccessToken(String userId, String username, String clientId, String scope) {
        long now = Instant.now().getEpochSecond();
        long exp = now + props.getAccessTokenTtlSeconds();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", props.getIssuer());
        payload.put("sub", userId);
        payload.put("username", username);
        payload.put("client_id", clientId);
        payload.put("scope", scope);
        payload.put("typ", "access");
        payload.put("iat", now);
        payload.put("exp", exp);
        payload.put("jti", randomId(16));
        try {
            String h = B64URL.encodeToString(objectMapper.writeValueAsBytes(header));
            String p = B64URL.encodeToString(objectMapper.writeValueAsBytes(payload));
            String signingInput = h + "." + p;
            String sig = B64URL.encodeToString(hmac(signingInput));
            return signingInput + "." + sig;
        } catch (Exception e) {
            throw new IllegalStateException("签发 JWT 失败", e);
        }
    }

    /** 验签并解析 access_token；签名无效、格式错误、过期、类型不符均返回 null。 */
    public AccessTokenClaims verifyAccessToken(String compact) {
        if (compact == null) {
            return null;
        }
        String[] parts = compact.split("\\.", 3);
        if (parts.length != 3) {
            return null;
        }
        try {
            JsonNode headerNode = objectMapper.readTree(B64URL_DEC.decode(parts[0]));
            if (!"HS256".equals(headerNode.path("alg").asText(null))) {
                return null;
            }
            byte[] expectedSig = hmac(parts[0] + "." + parts[1]);
            if (!MessageDigest.isEqual(expectedSig, B64URL_DEC.decode(parts[2]))) {
                return null;
            }
            JsonNode node = objectMapper.readTree(B64URL_DEC.decode(parts[1]));
            long exp = node.path("exp").asLong(0);
            if (exp <= Instant.now().getEpochSecond()) {
                return null;
            }
            if (!props.getIssuer().equals(node.path("iss").asText(null))) {
                return null;
            }
            if (!"access".equals(node.path("typ").asText(null))) {
                return null;
            }
            return new AccessTokenClaims(
                    text(node, "jti"), text(node, "sub"), text(node, "username"),
                    text(node, "client_id"), text(node, "scope"),
                    node.path("iat").asLong(0), exp);
        } catch (Exception e) {
            log.debug("JWT 验签失败: {}", e.getMessage());
            return null;
        }
    }

    /** 签发授权页登录会话 Cookie 值：userId.exp.signature（签名防伪造，服务端无状态）。 */
    public String issueSessionCookie(String userId) {
        long exp = Instant.now().getEpochSecond() + props.getSessionTtlSeconds();
        String payload = userId + "." + exp;
        return payload + "." + hmacHex(payload);
    }

    /** 校验会话 Cookie，返回 userId；无效/过期返回 null。 */
    public String verifySessionCookie(String value) {
        if (value == null) {
            return null;
        }
        int lastDot = value.lastIndexOf('.');
        if (lastDot < 0) {
            return null;
        }
        String payload = value.substring(0, lastDot);
        String sig = value.substring(lastDot + 1);
        if (!MessageDigest.isEqual(
                hmacHex(payload).getBytes(StandardCharsets.UTF_8),
                sig.getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        int firstDot = payload.indexOf('.');
        if (firstDot < 0) {
            return null;
        }
        try {
            long exp = Long.parseLong(payload.substring(firstDot + 1));
            if (exp <= Instant.now().getEpochSecond()) {
                return null;
            }
            return payload.substring(0, firstDot);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public long getSessionTtlSeconds() {
        return props.getSessionTtlSeconds();
    }

    public long getRefreshTtlSeconds() {
        return props.getRefreshTokenTtlDays() * 86400;
    }

    public long getCodeTtlSeconds() {
        return props.getCodeTtlSeconds();
    }

    /** 生成不透明随机令牌（base64url）。 */
    public String generateOpaqueToken(int bytes) {
        byte[] buf = new byte[bytes];
        random.nextBytes(buf);
        return B64URL.encodeToString(buf);
    }

    /** 随机 ID（jti 等）。 */
    public String randomId(int bytes) {
        byte[] buf = new byte[bytes];
        random.nextBytes(buf);
        return B64URL.encodeToString(buf);
    }

    /** 令牌落库前做 SHA-256 哈希（hex 小写）。 */
    public static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算失败", e);
        }
    }

    private byte[] hmac(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(props.getJwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    private String hmacHex(String content) {
        byte[] raw = hmac(content);
        StringBuilder sb = new StringBuilder(raw.length * 2);
        for (byte b : raw) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
