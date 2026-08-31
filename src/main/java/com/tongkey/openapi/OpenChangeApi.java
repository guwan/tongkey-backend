package com.tongkey.openapi;

import com.tongkey.common.ApiResponse;
import com.tongkey.domain.ChangeAction;
import com.tongkey.domain.EntityType;
import com.tongkey.domain.entity.AuditLog;
import com.tongkey.domain.repository.AuditLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 开放 API：变更查询（规格文档 7.1）。
 * <p>面向不希望被动接收推送、而是想自己定时拉增量的第三方：
 * {@code GET /api/v1/changes?since={epochMillis}&entity=USER}。</p>
 */
@Tag(name = "开放API-变更查询", description = "第三方定时拉取增量变更（替代被动推送的方案）")
@SecurityRequirement(name = "ApiKeyAuth")
@RestController
@RequestMapping("/api/v1/changes")
public class OpenChangeApi {

    private final AuditLogRepository auditLogRepository;

    public OpenChangeApi(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Operation(summary = "拉取指定时间之后的数据变更",
            description = "since 为 epoch 毫秒；entity 可选 USER/ROLE/PERMISSION/USER_ROLE/ROLE_PERMISSION；单页最多 500 条")
    @GetMapping
    public ApiResponse<Map<String, Object>> changes(@RequestParam long since,
                                                    @RequestParam(required = false) EntityType entity,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "100") int size) {
        OpenApiContext.requireScope("change:read");
        Instant sinceInstant = Instant.ofEpochMilli(since);
        PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 500));
        var result = entity != null
                ? auditLogRepository.findByEntityTypeAndCreatedAtAfterOrderByCreatedAtDesc(entity, sinceInstant, pr)
                : auditLogRepository.findByCreatedAtAfterOrderByCreatedAtDesc(sinceInstant, pr);
        List<Map<String, Object>> items = result.getContent().stream().map(OpenChangeApi::toChange).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", result.getTotalElements());
        data.put("page", result.getNumber());
        data.put("size", result.getSize());
        data.put("serverTimeMillis", System.currentTimeMillis());
        data.put("items", items);
        return ApiResponse.ok(data);
    }

    private static Map<String, Object> toChange(AuditLog l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("changeId", l.getId());
        m.put("entityType", l.getEntityType().name());
        m.put("entityId", l.getEntityId());
        m.put("entityCode", l.getEntityCode());
        m.put("action", l.getAction() == null ? ChangeAction.UPDATE.name() : l.getAction().name());
        m.put("channel", l.getChannel());
        m.put("occurredAt", l.getCreatedAt() == null ? null : l.getCreatedAt().toEpochMilli());
        return m;
    }
}
