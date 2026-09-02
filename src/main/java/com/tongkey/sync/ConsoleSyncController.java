package com.tongkey.sync;

import com.tongkey.common.ApiException;
import com.tongkey.common.ApiResponse;
import com.tongkey.common.ErrorCode;
import com.tongkey.common.PageData;
import com.tongkey.datasource.SyncMode;
import com.tongkey.domain.ConflictStrategy;
import com.tongkey.domain.EntityType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
 * 管理控制台：同步映射管理、手动触发、SQL 在线调试、同步日志（规格文档第 9 章）。
 */
@Tag(name = "控制台-同步管理")
@RestController
@RequestMapping("/console")
public class ConsoleSyncController {

    private final SyncMappingRepository mappingRepository;
    private final SyncLogRepository syncLogRepository;
    private final SyncEngine syncEngine;
    private final SyncScheduler scheduler;
    private final SqlReadonlyValidator sqlValidator;

    public ConsoleSyncController(SyncMappingRepository mappingRepository, SyncLogRepository syncLogRepository,
                                 SyncEngine syncEngine, SyncScheduler scheduler, SqlReadonlyValidator sqlValidator) {
        this.mappingRepository = mappingRepository;
        this.syncLogRepository = syncLogRepository;
        this.syncEngine = syncEngine;
        this.scheduler = scheduler;
        this.sqlValidator = sqlValidator;
    }

    public record MappingRequest(@NotBlank String name, @NotNull EntityType targetEntity, @NotBlank String sqlText,
                                 @NotBlank String fieldMapping, ConflictStrategy conflictStrategy,
                                 Integer batchSize, Boolean enabled,
                                 SyncMode syncMode, String incrementalColumn, String scheduleCron) {
    }

    @Operation(summary = "数据源下的映射任务列表")
    @GetMapping("/datasources/{dsId}/mappings")
    public ApiResponse<List<SyncMapping>> listMappings(@PathVariable String dsId) {
        return ApiResponse.ok(mappingRepository.findByDataSourceId(dsId));
    }

    @Operation(summary = "新建映射任务", description = "保存前自动做 SQL 只读校验")
    @PostMapping("/datasources/{dsId}/mappings")
    public ApiResponse<SyncMapping> createMapping(@PathVariable String dsId,
                                                  @RequestBody @jakarta.validation.Valid MappingRequest req) {
        sqlValidator.validate(req.sqlText());
        SyncMapping m = new SyncMapping();
        m.setDataSourceId(dsId);
        apply(m, req);
        SyncMapping saved = mappingRepository.save(m);
        scheduler.refresh();
        return ApiResponse.ok(saved);
    }

    @Operation(summary = "更新映射任务")
    @PutMapping("/mappings/{id}")
    public ApiResponse<SyncMapping> updateMapping(@PathVariable String id,
                                                  @RequestBody @jakarta.validation.Valid MappingRequest req) {
        SyncMapping m = requireMapping(id);
        sqlValidator.validate(req.sqlText());
        apply(m, req);
        SyncMapping saved = mappingRepository.save(m);
        scheduler.refresh();
        return ApiResponse.ok(saved);
    }

    @Operation(summary = "删除映射任务")
    @DeleteMapping("/mappings/{id}")
    public ApiResponse<Void> deleteMapping(@PathVariable String id) {
        requireMapping(id);
        mappingRepository.deleteById(id);
        scheduler.refresh();
        return ApiResponse.ok();
    }

    @Operation(summary = "手动触发单个映射同步")
    @PostMapping("/mappings/{id}/run")
    public ApiResponse<SyncLog> runMapping(@PathVariable String id) {
        return ApiResponse.ok(syncEngine.runMapping(id, SyncLog.SyncTrigger.MANUAL));
    }

    @Operation(summary = "手动触发数据源下全部映射同步")
    @PostMapping("/datasources/{dsId}/run")
    public ApiResponse<List<SyncLog>> runDataSource(@PathVariable String dsId) {
        return ApiResponse.ok(syncEngine.runDataSource(dsId, SyncLog.SyncTrigger.MANUAL));
    }

    @Operation(summary = "SQL 在线调试预览", description = "只读校验 + 方言限行包装后实际执行，返回样例数据与拼参后的语句")
    @PostMapping("/datasources/{dsId}/sql-preview")
    public ApiResponse<Map<String, Object>> preview(@PathVariable String dsId, @RequestBody SqlPreviewRequest req) {
        return ApiResponse.ok(syncEngine.preview(dsId, req.sql(), req.limit()));
    }

    @Operation(summary = "映射的同步日志")
    @GetMapping("/mappings/{id}/logs")
    public ApiResponse<PageData<SyncLog>> mappingLogs(@PathVariable String id,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(PageData.of(syncLogRepository.findByMappingIdOrderByStartedAtDesc(
                id, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)))));
    }

    @Operation(summary = "同步日志（全局/按数据源）")
    @GetMapping("/sync-logs")
    public ApiResponse<PageData<SyncLog>> logs(@RequestParam(required = false) String dataSourceId,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        var result = dataSourceId != null
                ? syncLogRepository.findByDataSourceIdOrderByStartedAtDesc(dataSourceId, pr)
                : syncLogRepository.findAllByOrderByStartedAtDesc(pr);
        return ApiResponse.ok(PageData.of(result));
    }

    public record SqlPreviewRequest(@NotBlank String sql, Integer limit) {
    }

    private SyncMapping requireMapping(String id) {
        return mappingRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "映射配置不存在: " + id));
    }

    private void apply(SyncMapping m, MappingRequest r) {
        m.setName(r.name());
        m.setTargetEntity(r.targetEntity());
        m.setSqlText(r.sqlText());
        m.setFieldMapping(r.fieldMapping());
        m.setConflictStrategy(r.conflictStrategy() != null ? r.conflictStrategy() : ConflictStrategy.SYNC_OVERRIDE);
        m.setBatchSize(r.batchSize() != null && r.batchSize() > 0 ? r.batchSize() : 500);
        m.setEnabled(r.enabled() == null || r.enabled());
        m.setSyncMode(r.syncMode() != null ? r.syncMode() : SyncMode.FULL);
        m.setIncrementalColumn(r.incrementalColumn());
        String cron = r.scheduleCron();
        m.setScheduleCron(cron == null || cron.isBlank() ? null : cron.trim());
    }
}
