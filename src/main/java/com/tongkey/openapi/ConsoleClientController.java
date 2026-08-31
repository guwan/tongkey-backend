package com.tongkey.openapi;

import com.tongkey.common.ApiResponse;
import com.tongkey.common.CryptoUtil;
import com.tongkey.common.ErrorCode;
import com.tongkey.common.ApiException;
import com.tongkey.common.PageData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理控制台：开放 API 接入方（Client / API Key）管理与调用日志（规格文档第 9 章）。
 */
@Tag(name = "控制台-开放API管理")
@RestController
@RequestMapping("/console/clients")
public class ConsoleClientController {

    private final ClientRepository clientRepository;
    private final ApiAccessLogRepository accessLogRepository;
    private final RateLimiterRegistry rateLimiter;
    private final CryptoUtil crypto;
    private final SecureRandom random = new SecureRandom();

    public ConsoleClientController(ClientRepository clientRepository, ApiAccessLogRepository accessLogRepository,
                                   RateLimiterRegistry rateLimiter, CryptoUtil crypto) {
        this.clientRepository = clientRepository;
        this.accessLogRepository = accessLogRepository;
        this.rateLimiter = rateLimiter;
        this.crypto = crypto;
    }

    public record ClientRequest(@NotBlank String clientId, @NotBlank String name, String scopes,
                                Integer qpsLimit, Boolean requireSignature, Boolean enabled) {
    }

    public record ClientView(String id, String clientId, String name, String apiKey, String scopes,
                             int qpsLimit, boolean requireSignature, boolean enabled,
                             java.time.Instant createdAt) {
        static ClientView of(ClientEntity c) {
            return new ClientView(c.getId(), c.getClientId(), c.getName(), c.getApiKey(), c.getScopes(),
                    c.getQpsLimit(), c.isRequireSignature(), c.isEnabled(), c.getCreatedAt());
        }
    }

    @Operation(summary = "接入方列表")
    @GetMapping
    public ApiResponse<List<ClientView>> list() {
        return ApiResponse.ok(clientRepository.findAll().stream().map(ClientView::of).toList());
    }

    @Operation(summary = "创建接入方", description = "自动生成 API Key 与 Client Secret，Secret 仅本次返回明文，请妥善保存")
    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody @jakarta.validation.Valid ClientRequest req) {
        if (clientRepository.findByClientId(req.clientId()).isPresent()) {
            throw new ApiException(ErrorCode.DUPLICATE_KEY, "client_id 已存在: " + req.clientId());
        }
        ClientEntity c = new ClientEntity();
        c.setClientId(req.clientId());
        apply(c, req);
        String plainSecret = randomToken(32);
        c.setClientSecret(crypto.encrypt(plainSecret));
        c.setApiKey("tk_" + randomToken(24));
        c = clientRepository.save(c);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("client", ClientView.of(c));
        data.put("clientSecret", plainSecret);
        data.put("warning", "clientSecret 仅本次显示，请立即保存；签名校验（防重放）需使用该密钥");
        return ApiResponse.ok(data);
    }

    @Operation(summary = "更新接入方（权限/限流/启用状态）")
    @PutMapping("/{id}")
    public ApiResponse<ClientView> update(@PathVariable String id, @RequestBody @jakarta.validation.Valid ClientRequest req) {
        ClientEntity c = require(id);
        apply(c, req);
        c = clientRepository.save(c);
        rateLimiter.evict(c.getClientId());
        return ApiResponse.ok(ClientView.of(c));
    }

    @Operation(summary = "重置 API Key", description = "旧 Key 立即失效")
    @PostMapping("/{id}/reset-key")
    public ApiResponse<Map<String, Object>> resetKey(@PathVariable String id) {
        ClientEntity c = require(id);
        c.setApiKey("tk_" + randomToken(24));
        c = clientRepository.save(c);
        return ApiResponse.ok(Map.of("clientId", c.getClientId(), "apiKey", c.getApiKey()));
    }

    @Operation(summary = "删除接入方")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        ClientEntity c = require(id);
        clientRepository.delete(c);
        rateLimiter.evict(c.getClientId());
        return ApiResponse.ok();
    }

    @Operation(summary = "开放 API 调用日志", description = "可按 clientId 过滤")
    @GetMapping("/access-logs")
    public ApiResponse<PageData<ApiAccessLog>> accessLogs(@RequestParam(required = false) String clientId,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        var result = clientId != null
                ? accessLogRepository.findByClientIdOrderByCreatedAtDesc(clientId, pr)
                : accessLogRepository.findAllByOrderByCreatedAtDesc(pr);
        return ApiResponse.ok(PageData.of(result));
    }

    private ClientEntity require(String id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "接入方不存在: " + id));
    }

    private void apply(ClientEntity c, ClientRequest r) {
        c.setName(r.name());
        if (r.scopes() != null && !r.scopes().isBlank()) {
            c.setScopes(r.scopes());
        }
        if (r.qpsLimit() != null && r.qpsLimit() > 0) {
            c.setQpsLimit(r.qpsLimit());
        }
        if (r.requireSignature() != null) {
            c.setRequireSignature(r.requireSignature());
        }
        c.setEnabled(r.enabled() == null || r.enabled());
    }

    private String randomToken(int bytes) {
        byte[] buf = new byte[bytes];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
