package com.tongkey.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongkey.common.ApiResponse;
import com.tongkey.common.ErrorCode;
import com.tongkey.common.OperatorContext;
import com.tongkey.console.AdminTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 管理控制台 Bearer Token 鉴权过滤器。
 * <p>注意：不能标注 {@code @Component}，否则会被 Spring Boot 额外注册为全局 Servlet 过滤器，
 * 绕过安全链的路径匹配拦截所有请求（含文档与健康检查）。仅由 {@link SecurityConfig} 注入到控制台链。
 */
public class ConsoleAuthFilter extends OncePerRequestFilter {

    private final AdminTokenService tokenService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ConsoleAuthFilter(AdminTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/console/auth/login")
                || !path.startsWith("/console");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String auth = request.getHeader("Authorization");
        String token = auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : null;
        if (!tokenService.isValid(token)) {
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.error(ErrorCode.UNAUTHORIZED, "控制台会话无效或已过期，请重新登录")));
            return;
        }
        OperatorContext.set("admin", OperatorContext.CHANNEL_CONSOLE);
        try {
            chain.doFilter(request, response);
        } finally {
            OperatorContext.clear();
        }
    }
}
