package com.tongkey.datasource;

import com.tongkey.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 管理控制台：第三方数据源管理（规格文档第 9 章）。
 */
@Tag(name = "控制台-数据源管理")
@RestController
@RequestMapping("/console/datasources")
public class ConsoleDataSourceController {

    private final DataSourceService service;

    public ConsoleDataSourceController(DataSourceService service) {
        this.service = service;
    }

    public record DataSourceRequest(@NotBlank String name, @NotNull DbType dbType, @NotBlank String jdbcUrl,
                                    String username, String password, Boolean enabled, String scheduleCron,
                                    SyncMode syncMode, String incrementalColumn, Integer connectTimeoutSeconds,
                                    String notes) {
    }

    public record DataSourceView(String id, String name, DbType dbType, String jdbcUrl, String username,
                                 boolean enabled, String scheduleCron, SyncMode syncMode, String incrementalColumn,
                                 int connectTimeoutSeconds, String notes, Instant createdAt, Instant updatedAt) {
        static DataSourceView of(DataSourceConfig c) {
            return new DataSourceView(c.getId(), c.getName(), c.getDbType(), c.getJdbcUrl(), c.getUsername(),
                    c.isEnabled(), c.getScheduleCron(), c.getSyncMode(), c.getIncrementalColumn(),
                    c.getConnectTimeoutSeconds(), c.getNotes(), c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    @Operation(summary = "数据源列表", description = "密码加密存储，接口永不明文返回")
    @GetMapping
    public ApiResponse<List<DataSourceView>> list() {
        return ApiResponse.ok(service.listAll().stream().map(DataSourceView::of).toList());
    }

    @Operation(summary = "新建数据源")
    @PostMapping
    public ApiResponse<DataSourceView> create(@RequestBody @jakarta.validation.Valid DataSourceRequest req) {
        return ApiResponse.ok(DataSourceView.of(service.create(toEntity(req, null))));
    }

    @Operation(summary = "更新数据源", description = "密码传空或脱敏占位表示保持原值")
    @PutMapping("/{id}")
    public ApiResponse<DataSourceView> update(@PathVariable String id, @RequestBody @jakarta.validation.Valid DataSourceRequest req) {
        return ApiResponse.ok(DataSourceView.of(service.update(id, toEntity(req, id))));
    }

    @Operation(summary = "删除数据源")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "测试连接", description = "按 db_type 执行最小化探测查询（如 Oracle SELECT 1 FROM DUAL）")
    @PostMapping("/{id}/test")
    public ApiResponse<Map<String, Object>> test(@PathVariable String id) {
        long cost = service.testConnection(id);
        return ApiResponse.ok(Map.of("connected", true, "costMs", cost));
    }

    private DataSourceConfig toEntity(DataSourceRequest r, String id) {
        DataSourceConfig c = new DataSourceConfig();
        if (id != null) {
            c.setId(id);
        }
        c.setName(r.name());
        c.setDbType(r.dbType());
        c.setJdbcUrl(r.jdbcUrl());
        c.setUsername(r.username());
        c.setPassword(r.password());
        c.setEnabled(r.enabled() == null || r.enabled());
        c.setScheduleCron(r.scheduleCron());
        c.setSyncMode(r.syncMode() != null ? r.syncMode() : SyncMode.FULL);
        c.setIncrementalColumn(r.incrementalColumn());
        c.setConnectTimeoutSeconds(r.connectTimeoutSeconds() != null ? r.connectTimeoutSeconds() : 10);
        c.setNotes(r.notes());
        return c;
    }
}
