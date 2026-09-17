package com.tongkey.openapi;

import com.tongkey.common.ApiResponse;
import com.tongkey.domain.entity.UserEntity;
import com.tongkey.domain.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 开放 API：当前令牌身份探查。
 * <p>两种通道均可调用（无需特定 scope）：
 * <ul>
 *   <li>X-API-Key：返回接入方信息与其全量 scope；</li>
 *   <li>OAuth2 Bearer：返回授权用户信息、接入方信息与令牌实际 scope。</li>
 * </ul>
 */
@Tag(name = "开放API-身份", description = "查看当前调用令牌的身份与权限范围，支持 X-API-Key 与 OAuth2 Bearer")
@SecurityRequirement(name = "ApiKeyAuth")
@SecurityRequirement(name = "BearerAuth")
@RestController
@RequestMapping("/api/v1/me")
public class OpenMeApi {

    private final UserRepository userRepository;

    public OpenMeApi(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Operation(summary = "当前令牌身份", description = "返回调用通道（api_key/oauth2）、接入方、生效 scope；OAuth2 通道额外返回授权用户")
    @GetMapping
    public ApiResponse<Map<String, Object>> me() {
        ClientEntity client = OpenApiContext.current();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channel", OpenApiContext.channel());

        Map<String, Object> clientInfo = new LinkedHashMap<>();
        clientInfo.put("clientId", client.getClientId());
        clientInfo.put("name", client.getName());
        body.put("client", clientInfo);
        body.put("scopes", OpenApiContext.effectiveScopes());

        if (OpenApiContext.CHANNEL_OAUTH2.equals(OpenApiContext.channel())) {
            Map<String, Object> userInfo = new LinkedHashMap<>();
            userInfo.put("id", OpenApiContext.currentUserId());
            userInfo.put("username", OpenApiContext.currentUsername());
            userRepository.findById(OpenApiContext.currentUserId())
                    .map(UserEntity::getDisplayName)
                    .ifPresent(name -> userInfo.put("displayName", name));
            body.put("user", userInfo);
        }
        return ApiResponse.ok(body);
    }
}
