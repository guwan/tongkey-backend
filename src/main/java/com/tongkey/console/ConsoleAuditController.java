package com.tongkey.console;

import com.tongkey.common.ApiResponse;
import com.tongkey.common.PageData;
import com.tongkey.domain.EntityType;
import com.tongkey.domain.entity.AuditLog;
import com.tongkey.domain.repository.AuditLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 管理控制台：全局审计日志（规格文档第 9 章）。
 */
@Tag(name = "控制台-审计日志")
@RestController
@RequestMapping("/console/audit")
public class ConsoleAuditController {

    private final AuditLogRepository auditLogRepository;

    public ConsoleAuditController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Operation(summary = "分页查询审计日志", description = "可按实体类型与起始时间过滤；渠道字段标识变更来源（CONSOLE / API:xxx / SYNC:xxx）")
    @GetMapping
    public ApiResponse<PageData<AuditLog>> list(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) EntityType entityType,
                                                @RequestParam(required = false) Long sinceMillis) {
        PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200));
        Instant since = sinceMillis != null ? Instant.ofEpochMilli(sinceMillis) : null;
        Page<AuditLog> result;
        if (entityType != null && since != null) {
            result = auditLogRepository.findByEntityTypeAndCreatedAtAfterOrderByCreatedAtDesc(entityType, since, pr);
        } else if (since != null) {
            result = auditLogRepository.findByCreatedAtAfterOrderByCreatedAtDesc(since, pr);
        } else {
            result = auditLogRepository.findAllByOrderByCreatedAtDesc(pr);
        }
        return ApiResponse.ok(PageData.of(result));
    }
}
