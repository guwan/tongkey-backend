package com.tongkey.console;

import com.tongkey.common.ApiResponse;
import com.tongkey.common.PageData;
import com.tongkey.domain.ResourceType;
import com.tongkey.domain.SourceType;
import com.tongkey.domain.service.DomainQueryService;
import com.tongkey.domain.service.DomainWriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理控制台：权限管理。
 */
@Tag(name = "控制台-权限管理")
@RestController
@RequestMapping("/console/permissions")
public class ConsolePermissionController {

    private final DomainQueryService query;
    private final DomainWriteService write;

    public ConsolePermissionController(DomainQueryService query, DomainWriteService write) {
        this.query = query;
        this.write = write;
    }

    @Operation(summary = "分页查询权限")
    @GetMapping
    public ApiResponse<PageData<DomainDtos.PermissionView>> list(@RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size,
                                                                 @RequestParam(required = false) String keyword,
                                                                 @RequestParam(required = false) SourceType sourceType,
                                                                 @RequestParam(required = false) ResourceType resourceType) {
        return ApiResponse.ok(PageData.map(query.pagePermissions(page, size, keyword, sourceType, resourceType),
                DomainDtos.PermissionView::of));
    }

    @Operation(summary = "新建权限")
    @PostMapping
    public ApiResponse<DomainDtos.PermissionView> create(@RequestBody @jakarta.validation.Valid DomainDtos.PermissionRequest req) {
        return ApiResponse.ok(DomainDtos.PermissionView.of(write.createPermission(req.code(), req.name(),
                req.description(), parseType(req.resourceType()), req.extraAttrs())));
    }

    @Operation(summary = "更新权限")
    @PutMapping("/{id}")
    public ApiResponse<DomainDtos.PermissionView> update(@PathVariable String id, @RequestBody DomainDtos.PermissionRequest req) {
        return ApiResponse.ok(DomainDtos.PermissionView.of(write.updatePermission(id, req.name(),
                req.description(), parseType(req.resourceType()), req.extraAttrs())));
    }

    @Operation(summary = "删除权限")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        write.deletePermission(id);
        return ApiResponse.ok();
    }

    private static ResourceType parseType(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return ResourceType.valueOf(s.trim().toUpperCase());
    }
}
