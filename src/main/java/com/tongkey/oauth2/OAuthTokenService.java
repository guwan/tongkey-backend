package com.tongkey.oauth2;

import com.tongkey.domain.entity.UserEntity;
import com.tongkey.openapi.ClientEntity;
import com.tongkey.openapi.ClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * OAuth2 令牌编排：授权码生成/核销、access/refresh 签发与刷新令牌旋转。
 * 授权码与刷新令牌的明文只在签发瞬间返回，库内仅存 SHA-256 哈希。
 */
@Service
public class OAuthTokenService {

    private final OAuthCodeRepository codeRepository;
    private final OAuthRefreshTokenRepository refreshRepository;
    private final ClientRepository clientRepository;
    private final JwtTokenService jwt;
    private final OAuth2Properties props;

    public OAuthTokenService(OAuthCodeRepository codeRepository,
                             OAuthRefreshTokenRepository refreshRepository,
                             ClientRepository clientRepository,
                             JwtTokenService jwt,
                             OAuth2Properties props) {
        this.codeRepository = codeRepository;
        this.refreshRepository = refreshRepository;
        this.clientRepository = clientRepository;
        this.jwt = jwt;
        this.props = props;
    }

    /** token 端点成功响应载荷。 */
    public record IssuedTokens(String accessToken, String refreshToken, long expiresIn, String scope) {
    }

    /** 用户同意后创建授权码，返回明文 code（仅此刻可见）。 */
    @Transactional
    public String createAuthorizationCode(ClientEntity client, UserEntity user, String grantedScopes,
                                          String redirectUri) {
        String rawCode = jwt.generateOpaqueToken(32);
        OAuthCodeEntity entity = new OAuthCodeEntity();
        entity.setCodeHash(JwtTokenService.sha256Hex(rawCode));
        entity.setClientId(client.getClientId());
        entity.setUserId(user.getId());
        entity.setUsername(user.getUsername());
        entity.setScopes(grantedScopes);
        entity.setRedirectUri(redirectUri);
        entity.setExpiresAt(Instant.now().plusSeconds(props.getCodeTtlSeconds()));
        entity.setUsed(false);
        codeRepository.save(entity);
        return rawCode;
    }

    /** authorization_code 换令牌：校验 code 归属、一次性、重定向一致，成功立即标记 used。 */
    @Transactional
    public IssuedTokens exchangeCode(String rawCode, String clientId, String redirectUri) {
        ClientEntity client = requireClient(clientId);
        if (rawCode == null || rawCode.isBlank()) {
            throw new OAuthErrorException(400, "invalid_request", "缺少 code 参数");
        }
        OAuthCodeEntity code = codeRepository.findByCodeHash(JwtTokenService.sha256Hex(rawCode))
                .orElseThrow(() -> new OAuthErrorException(400, "invalid_grant", "授权码无效"));
        if (code.getExpiresAt().isBefore(Instant.now())) {
            throw new OAuthErrorException(400, "invalid_grant", "授权码无效或已过期");
        }
        if (!code.getClientId().equals(clientId)) {
            throw new OAuthErrorException(400, "invalid_grant", "授权码与接入方不匹配");
        }
        if (redirectUri == null || !redirectUri.equals(code.getRedirectUri())) {
            throw new OAuthErrorException(400, "invalid_grant", "redirect_uri 与授权时不一致");
        }
        // 原子条件更新，保证并发下 code 也只能被核销一次
        if (codeRepository.markUsed(code.getId()) == 0) {
            throw new OAuthErrorException(400, "invalid_grant", "授权码已被使用");
        }
        return issueTokens(client, code.getUserId(), code.getUsername(), code.getScopes());
    }

    /** refresh_token 刷新：旧令牌旋转作废，签发新的 access + refresh。 */
    @Transactional
    public IssuedTokens refresh(String rawRefreshToken, String clientId) {
        ClientEntity client = requireClient(clientId);
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new OAuthErrorException(400, "invalid_request", "缺少 refresh_token 参数");
        }
        OAuthRefreshTokenEntity old = refreshRepository
                .findByTokenHash(JwtTokenService.sha256Hex(rawRefreshToken))
                .orElseThrow(() -> new OAuthErrorException(400, "invalid_grant", "refresh_token 无效"));
        if (old.getExpiresAt().isBefore(Instant.now())) {
            throw new OAuthErrorException(400, "invalid_grant", "refresh_token 已过期");
        }
        if (!old.getClientId().equals(clientId)) {
            throw new OAuthErrorException(400, "invalid_grant", "refresh_token 与接入方不匹配");
        }
        // 原子旋转，并发下旧令牌也只能成功一次
        if (refreshRepository.markRevoked(old.getId()) == 0) {
            throw new OAuthErrorException(400, "invalid_grant", "refresh_token 已被使用或撤销");
        }
        return issueTokens(client, old.getUserId(), old.getUsername(), old.getScopes());
    }

    private IssuedTokens issueTokens(ClientEntity client, String userId, String username, String grantedScopes) {
        // 刷新时若接入方事后被收紧 scope，实际可用 scope 取交集
        String effective = intersectScopes(grantedScopes, client.getScopes());
        String accessToken = jwt.issueAccessToken(userId, username, client.getClientId(), effective);
        String rawRefresh = jwt.generateOpaqueToken(32);

        OAuthRefreshTokenEntity refresh = new OAuthRefreshTokenEntity();
        refresh.setTokenHash(JwtTokenService.sha256Hex(rawRefresh));
        refresh.setClientId(client.getClientId());
        refresh.setUserId(userId);
        refresh.setUsername(username);
        refresh.setScopes(effective);
        refresh.setExpiresAt(Instant.now().plusSeconds(jwt.getRefreshTtlSeconds()));
        refresh.setRevoked(false);
        refreshRepository.save(refresh);

        return new IssuedTokens(accessToken, rawRefresh, props.getAccessTokenTtlSeconds(), effective);
    }

    private String intersectScopes(String grantedSpaceSeparated, String clientCsv) {
        var granted = OAuthClientSupport.parseGrantedScopes(grantedSpaceSeparated);
        var clientScopes = OAuthClientSupport.scopeSet(clientCsv);
        granted.retainAll(clientScopes);
        return String.join(" ", granted);
    }

    private ClientEntity requireClient(String clientId) {
        return clientRepository.findByClientId(clientId)
                .filter(ClientEntity::isEnabled)
                .orElseThrow(() -> new OAuthErrorException(401, "invalid_client", "接入方不存在或已停用"));
    }
}
