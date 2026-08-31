package com.tongkey.console;

import com.tongkey.common.ApiResponse;
import com.tongkey.common.ErrorCode;
import com.tongkey.common.OperatorContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理控制台登录（规格文档 12.1：本期内置账号，后续可接企业 SSO）。
 */
@Tag(name = "控制台-认证")
@RestController
@RequestMapping("/console/auth")
public class AuthController {

    private final AdminTokenService tokenService;

    public AuthController(AdminTokenService tokenService) {
        this.tokenService = tokenService;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    @Operation(summary = "登录", description = "使用管理控制台账号登录，返回 Bearer Token，有效期见服务端配置。")
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody LoginRequest req) {
        String token = tokenService.login(req.username(), req.password());
        if (token == null) {
            return ApiResponse.error(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        return ApiResponse.ok(Map.of("token", token, "username", req.username()));
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String auth) {
        tokenService.logout(extract(auth));
        return ApiResponse.ok();
    }

    @Operation(summary = "当前登录信息")
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        return ApiResponse.ok(Map.of("username", OperatorContext.operator(), "channel", OperatorContext.channel()));
    }

    static String extract(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return authHeader;
    }
}
