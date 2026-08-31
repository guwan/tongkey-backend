package com.tongkey.push;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongkey.common.ApiException;
import com.tongkey.common.ApiResponse;
import com.tongkey.common.CryptoUtil;
import com.tongkey.common.ErrorCode;
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

import java.util.List;
import java.util.Map;

/**
 * 管理控制台：推送目标管理、全量推送、推送日志与失败重推（规格文档第 9 章）。
 */
@Tag(name = "控制台-推送管理")
@RestController
@RequestMapping("/console/push")
public class ConsolePushController {

    private final PushTargetRepository targetRepository;
    private final PushLogRepository pushLogRepository;
    private final PushEngine pushEngine;
    private final CryptoUtil crypto;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ConsolePushController(PushTargetRepository targetRepository, PushLogRepository pushLogRepository,
                                 PushEngine pushEngine, CryptoUtil crypto) {
        this.targetRepository = targetRepository;
        this.pushLogRepository = pushLogRepository;
        this.pushEngine = pushEngine;
        this.crypto = crypto;
    }

    public record PushTargetRequest(@NotBlank String name, @NotBlank String endpointUrl, String httpMethod,
                                    PushAuthType authType, Map<String, String> authConfig, String triggerEvents,
                                    String entityScope, String payloadTemplate, Integer retryMax,
                                    Integer retryIntervalSeconds, Boolean enabled) {
    }

    public record PushTargetView(String id, String name, String endpointUrl, String httpMethod, PushAuthType authType,
                                 boolean hasAuthConfig, String triggerEvents, String entityScope, String payloadTemplate,
                                 int retryMax, int retryIntervalSeconds, boolean enabled) {
        static PushTargetView of(PushTarget t) {
            return new PushTargetView(t.getId(), t.getName(), t.getEndpointUrl(), t.getHttpMethod(), t.getAuthType(),
                    t.getAuthConfig() != null && !t.getAuthConfig().isBlank(), t.getTriggerEvents(), t.getEntityScope(),
                    t.getPayloadTemplate(), t.getRetryMax(), t.getRetryIntervalSeconds(), t.isEnabled());
        }
    }

    @Operation(summary = "推送目标列表", description = "鉴权密钥加密存储，接口不返回明文")
    @GetMapping("/targets")
    public ApiResponse<List<PushTargetView>> list() {
        return ApiResponse.ok(targetRepository.findAll().stream().map(PushTargetView::of).toList());
    }

    @Operation(summary = "新建推送目标", description = "启用且包含 ON_INIT 时自动触发一次全量推送（规格文档 6.2）")
    @PostMapping("/targets")
    public ApiResponse<PushTargetView> create(@RequestBody @jakarta.validation.Valid PushTargetRequest req) {
        PushTarget t = new PushTarget();
        boolean wasEnabled = false;
        apply(t, req);
        t = targetRepository.save(t);
        maybeInitPush(t, wasEnabled);
        return ApiResponse.ok(PushTargetView.of(t));
    }

    @Operation(summary = "更新推送目标", description = "authConfig 传空表示保持原密钥；禁用→启用且含 ON_INIT 时自动全量推送")
    @PutMapping("/targets/{id}")
    public ApiResponse<PushTargetView> update(@PathVariable String id, @RequestBody @jakarta.validation.Valid PushTargetRequest req) {
        PushTarget t = require(id);
        boolean wasEnabled = t.isEnabled();
        apply(t, req);
        t = targetRepository.save(t);
        maybeInitPush(t, wasEnabled);
        return ApiResponse.ok(PushTargetView.of(t));
    }

    @Operation(summary = "删除推送目标")
    @DeleteMapping("/targets/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        require(id);
        targetRepository.deleteById(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "手动全量推送一次", description = "将当前全部符合 entity_scope 的数据分批推送（ON_INIT）")
    @PostMapping("/targets/{id}/full-push")
    public ApiResponse<Map<String, Object>> fullPush(@PathVariable String id) {
        pushEngine.fullPushAsync(id);
        return ApiResponse.ok(Map.of("accepted", true, "message", "全量推送任务已提交，推送进度见推送日志"));
    }

    @Operation(summary = "推送日志", description = "可按推送目标过滤")
    @GetMapping("/logs")
    public ApiResponse<PageData<PushLog>> logs(@RequestParam(required = false) String targetId,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        var result = targetId != null
                ? pushLogRepository.findByTargetIdOrderByCreatedAtDesc(targetId, pr)
                : pushLogRepository.findAllByOrderByCreatedAtDesc(pr);
        return ApiResponse.ok(PageData.of(result));
    }

    @Operation(summary = "手动重推失败记录")
    @PostMapping("/logs/{logId}/retry")
    public ApiResponse<PushLog> retry(@PathVariable Long logId) {
        return ApiResponse.ok(pushEngine.manualRetry(logId));
    }

    private void maybeInitPush(PushTarget t, boolean wasEnabled) {
        if (t.isEnabled() && !wasEnabled
                && t.getTriggerEvents() != null && t.getTriggerEvents().contains(TriggerEvent.ON_INIT.name())) {
            pushEngine.fullPushAsync(t.getId());
        }
    }

    private PushTarget require(String id) {
        return targetRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "推送目标不存在: " + id));
    }

    private void apply(PushTarget t, PushTargetRequest r) {
        t.setName(r.name());
        t.setEndpointUrl(r.endpointUrl());
        t.setHttpMethod(r.httpMethod() != null ? r.httpMethod() : "POST");
        t.setAuthType(r.authType() != null ? r.authType() : PushAuthType.NONE);
        try {
            if (r.authConfig() != null && !r.authConfig().isEmpty()) {
                t.setAuthConfig(crypto.encrypt(objectMapper.writeValueAsString(r.authConfig())));
            }
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INVALID_PARAM, "authConfig 序列化失败: " + e.getMessage());
        }
        if (r.triggerEvents() != null && !r.triggerEvents().isBlank()) {
            t.setTriggerEvents(r.triggerEvents());
        }
        if (r.entityScope() != null && !r.entityScope().isBlank()) {
            t.setEntityScope(r.entityScope());
        }
        t.setPayloadTemplate(r.payloadTemplate());
        if (r.retryMax() != null && r.retryMax() >= 0) {
            t.setRetryMax(r.retryMax());
        }
        if (r.retryIntervalSeconds() != null && r.retryIntervalSeconds() > 0) {
            t.setRetryIntervalSeconds(r.retryIntervalSeconds());
        }
        t.setEnabled(r.enabled() == null || r.enabled());
    }
}
