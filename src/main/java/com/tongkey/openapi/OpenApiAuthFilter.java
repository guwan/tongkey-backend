package com.tongkey.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongkey.common.ApiResponse;
import com.tongkey.common.CryptoUtil;
import com.tongkey.common.ErrorCode;
import com.tongkey.push.PushEngine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * 开放 API 鉴权过滤器（规格文档 7.3）：
 * <ol>
 *   <li>X-API-Key 校验（Client 凭证）；</li>
 *   <li>按 Client 令牌桶限流；</li>
 *   <li>可选 HMAC-SHA256 签名 + 时间戳偏移校验（防重放）；</li>
 *   <li>全量访问日志（调用方、接口、参数摘要、状态、耗时）。</li>
 * </ol>
 * <p>注意：不能标注 {@code @Component}，否则会被 Spring Boot 额外注册为全局 Servlet 过滤器。
 */
public class OpenApiAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(OpenApiAuthFilter.class);

    private final ClientRepository clientRepository;
    private final ApiAccessLogRepository accessLogRepository;
    private final RateLimiterRegistry rateLimiter;
    private final CryptoUtil crypto;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${tongkey.openapi.signature-max-skew-seconds:300}")
    private long maxSkewSeconds;

    public OpenApiAuthFilter(ClientRepository clientRepository, ApiAccessLogRepository accessLogRepository,
                             RateLimiterRegistry rateLimiter, CryptoUtil crypto) {
        this.clientRepository = clientRepository;
        this.accessLogRepository = accessLogRepository;
        this.rateLimiter = rateLimiter;
        this.crypto = crypto;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        CachedBodyRequestWrapper wrapped = new CachedBodyRequestWrapper(request);
        String apiKey = wrapped.getHeader("X-API-Key");
        ClientEntity client = apiKey == null ? null : clientRepository.findByApiKey(apiKey).orElse(null);

        ErrorCode deny = null;
        if (client == null) {
            deny = ErrorCode.UNAUTHORIZED;
        } else if (!client.isEnabled()) {
            deny = ErrorCode.FORBIDDEN;
        } else if (!rateLimiter.tryAcquire(client.getClientId(), client.getQpsLimit())) {
            deny = ErrorCode.RATE_LIMITED;
        } else if (client.isRequireSignature()) {
            deny = verifySignature(wrapped, client);
        }

        if (deny != null) {
            writeError(response, deny);
            recordAccessLog(client, wrapped, 401, System.currentTimeMillis() - start);
            return;
        }

        OpenApiContext.set(client);
        try {
            chain.doFilter(wrapped, response);
        } finally {
            recordAccessLog(client, wrapped, response.getStatus(), System.currentTimeMillis() - start);
            OpenApiContext.clear();
        }
    }

    private ErrorCode verifySignature(HttpServletRequest request, ClientEntity client) {
        String timestamp = request.getHeader("X-Timestamp");
        String signature = request.getHeader("X-Signature");
        if (timestamp == null || signature == null) {
            return ErrorCode.SIGNATURE_INVALID;
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return ErrorCode.TIMESTAMP_EXPIRED;
        }
        long skew = Math.abs(Instant.now().getEpochSecond() - ts);
        if (skew > maxSkewSeconds) {
            return ErrorCode.TIMESTAMP_EXPIRED;
        }
        String body = OpenApiContext.cachedBody(request);
        String content = request.getMethod() + "\n" + request.getRequestURI() + "\n" + timestamp + "\n" + body;
        String expected = PushEngine.hmacSha256(crypto.decrypt(client.getClientSecret()), content);
        return expected.equalsIgnoreCase(signature) ? null : ErrorCode.SIGNATURE_INVALID;
    }

    private void writeError(HttpServletResponse response, ErrorCode ec) throws IOException {
        response.setStatus(ec == ErrorCode.RATE_LIMITED ? 429 : 401);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(ec)));
    }

    private void recordAccessLog(ClientEntity client, HttpServletRequest request, int status, long cost) {
        try {
            ApiAccessLog accessLog = new ApiAccessLog();
            accessLog.setClientId(client != null ? client.getClientId() : "unknown");
            accessLog.setMethod(request.getMethod());
            accessLog.setPath(request.getRequestURI());
            String query = request.getQueryString();
            accessLog.setParamSummary(query != null && query.length() > 1000 ? query.substring(0, 1000) : query);
            accessLog.setHttpStatus(status);
            accessLog.setCostMs(cost);
            accessLog.setRemoteIp(request.getRemoteAddr());
            accessLogRepository.save(accessLog);
        } catch (Exception e) {
            log.warn("记录访问日志失败: {}", e.getMessage());
        }
    }

    /** 预读请求体的包装器：签名校验与后续控制器各读一次。 */
    private static final class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequestWrapper(HttpServletRequest request) throws IOException {
            super(request);
            this.body = request.getInputStream().readAllBytes();
            request.setAttribute(OpenApiContext.ATTR_BODY, new String(body, StandardCharsets.UTF_8));
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // 同步读取，无需监听器
                }

                @Override
                public int read() {
                    return in.read();
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
