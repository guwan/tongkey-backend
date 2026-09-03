package com.tongkey.openapi;

import com.tongkey.common.ApiResponse;
import com.tongkey.sync.SyncEngine;
import com.tongkey.sync.SyncLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 开放 API：数据同步触发。
 * <p>鉴权：请求头 {@code X-API-Key}，需 scope {@code sync:run}。</p>
 */
@Tag(name = "开放API-同步", description = "供第三方系统触发数据同步，需 X-API-Key + sync:run scope")
@SecurityRequirement(name = "ApiKeyAuth")
@RestController
@RequestMapping("/api/v1/sync")
public class OpenSyncApi {

    private final SyncEngine syncEngine;

    public OpenSyncApi(SyncEngine syncEngine) {
        this.syncEngine = syncEngine;
    }

    @Operation(summary = "触发单个映射同步", description = "按映射 ID 手动执行一次同步，返回执行日志")
    @PostMapping("/mappings/{id}/run")
    public ApiResponse<SyncLog> runMapping(@PathVariable String id) {
        OpenApiContext.requireScope("sync:run");
        return ApiResponse.ok(syncEngine.runMapping(id, SyncLog.SyncTrigger.MANUAL));
    }

    @Operation(summary = "触发数据源下全部映射同步", description = "按数据源 ID 手动执行其下所有启用的映射任务，返回每个任务的执行日志")
    @PostMapping("/datasources/{dsId}/run")
    public ApiResponse<List<SyncLog>> runDataSource(@PathVariable String dsId) {
        OpenApiContext.requireScope("sync:run");
        return ApiResponse.ok(syncEngine.runDataSource(dsId, SyncLog.SyncTrigger.MANUAL));
    }
}
