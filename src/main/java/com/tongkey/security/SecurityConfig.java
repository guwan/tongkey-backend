package com.tongkey.security;

import com.tongkey.common.CryptoUtil;
import com.tongkey.console.AdminTokenService;
import com.tongkey.openapi.ApiAccessLogRepository;
import com.tongkey.openapi.ClientRepository;
import com.tongkey.openapi.OpenApiAuthFilter;
import com.tongkey.openapi.RateLimiterRegistry;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置（规格文档 7.3 / 10）：
 * <ul>
 *   <li>{@code /api/v1/**}：开放 API，由 {@link OpenApiAuthFilter} 完成 API Key/签名/限流/审计；</li>
 *   <li>{@code /console/**}：管理控制台，Bearer Token（内置账号，后续可接企业 SSO）；</li>
 *   <li>API 文档与健康检查开放访问。</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public OpenApiAuthFilter openApiAuthFilter(ClientRepository clientRepository,
                                               ApiAccessLogRepository accessLogRepository,
                                               RateLimiterRegistry rateLimiter, CryptoUtil crypto) {
        return new OpenApiAuthFilter(clientRepository, accessLogRepository, rateLimiter, crypto);
    }

    @Bean
    public ConsoleAuthFilter consoleAuthFilter(AdminTokenService tokenService) {
        return new ConsoleAuthFilter(tokenService);
    }

    /** 禁止 Spring Boot 将过滤器自动注册为全局 Servlet 过滤器，避免绕过安全链路径匹配。 */
    @Bean
    public FilterRegistrationBean<OpenApiAuthFilter> openApiAuthFilterRegistration(OpenApiAuthFilter filter) {
        FilterRegistrationBean<OpenApiAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<ConsoleAuthFilter> consoleAuthFilterRegistration(ConsoleAuthFilter filter) {
        FilterRegistrationBean<ConsoleAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /** 开放 API 链：鉴权/限流/签名/审计全部在过滤器内完成。 */
    @Bean
    @Order(1)
    public SecurityFilterChain openApiChain(HttpSecurity http, OpenApiAuthFilter openApiAuthFilter) throws Exception {
        return http
                .securityMatcher("/api/v1/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(openApiAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /** 管理控制台链 + 文档等公开路径。 */
    @Bean
    @Order(2)
    public SecurityFilterChain consoleChain(HttpSecurity http, ConsoleAuthFilter consoleAuthFilter) throws Exception {
        return http
                .securityMatcher("/console/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                        "/actuator/**", "/error")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/console/auth/login").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                                "/actuator/**", "/error").permitAll()
                        .anyRequest().permitAll())
                .addFilterBefore(consoleAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /** 其余路径默认放行（前端静态资源等）。 */
    @Bean
    @Order(3)
    public SecurityFilterChain defaultChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    @Bean
    public OpenAPI tongKeyOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TongKey 开放式授权中心 API")
                        .description("""
                                用户/角色/权限数据中心：开放接口供第三方系统查询与写入。

                                ## 鉴权
                                - 开放 API（/api/v1/**）：请求头携带 `X-API-Key`（管理台"开放 API 管理"中创建）。
                                - 部分接入方可被要求额外提供签名：`X-Timestamp`（epoch 秒）+ `X-Signature`
                                  （HMAC-SHA256(clientSecret, method + "\\n" + path + "\\n" + timestamp + "\\n" + body)，hex 小写）。

                                ## 通用约定
                                - 统一响应：`{ code, message, data, traceId }`，code=0 表示成功。
                                - 分页参数：`page`（0 起）、`size`。
                                - 写接口支持 `externalKey` 幂等：重复提交转为更新。
                                """)
                        .version("v1.0.0")
                        .contact(new Contact().name("TongKey"))
                        .license(new License().name("Internal Use")))
                .components(new Components().addSecuritySchemes("ApiKeyAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key")
                                .description("开放 API 凭证，在管理台创建接入方后获得")));
    }
}
