package com.tongkey.openapi;

import com.tongkey.common.ApiResponse;
import com.tongkey.common.PageData;
import com.tongkey.console.DomainDtos;
import com.tongkey.domain.ResourceType;
import com.tongkey.domain.SourceType;
import com.tongkey.domain.repository.PermissionRepository;
import com.tongkey.domain.service.DomainQueryService;
import com.tongkey.domain.service.DomainWriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 开放 API：权限。
 */
@Tag(name = "开放API-权限", description = "供第三方系统查询/创建/更新权限，需 X-API-Key 鉴权")
@SecurityRequirement(name = "ApiKeyAuth")
@RestController
@RequestMapping("/api/v1/permissions")
public class OpenPermissionApi {

    private final DomainQueryService query;
    private final DomainWriteService write;
    private final PermissionRepository permissionRepository;

    public OpenPermissionApi(DomainQueryService query, DomainWriteService write, PermissionRepository permissionRepository) {
        this.query = query;
        this.write = write;
        this.permissionRepository = permissionRepository;
    }

    public record PermissionWriteRequest(@NotBlank String code, String name, String description,
                                         String resourceType, String extraAttrs, String externalKey) {
    }

    @Operation(summary = "分页查询权限")
    @GetMapping
    public ApiResponse<PageData<DomainDtos.PermissionView>> list(@RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "20") int size,
                                                                 @RequestParam(required = false) String keyword,
                                                                 @RequestParam(required = false) SourceType sourceType,
                                                                 @RequestParam(required = false) ResourceType resourceType) {
        OpenApiContext.requireScope("permission:read");
        return ApiResponse.ok(PageData.map(query.pagePermissions(page, size, keyword, sourceType, resourceType),
                DomainDtos.PermissionView::of));
    }

    @Operation(summary = "权限详情")
    @GetMapping("/{id}")
    public ApiResponse<DomainDtos.PermissionView> get(@PathVariable String id) {
        OpenApiContext.requireScope("permission:read");
        return ApiResponse.ok(DomainDtos.PermissionView.of(write.requirePermission(id)));
    }

    @Operation(summary = "创建权限", description = "携带 externalKey 时具备幂等性")
    @PostMapping
    public ApiResponse<DomainDtos.PermissionView> create(@RequestBody @jakarta.validation.Valid PermissionWriteRequest req) {
        OpenApiContext.requireScope("permission:write");
        if (req.externalKey() != null) {
            var existing = permissionRepository.findFirstByExternalKey(req.externalKey());
            if (existing.isPresent()) {
                return ApiResponse.ok(DomainDtos.PermissionView.of(write.updatePermission(existing.get().getId(),
                        req.name(), req.description(), parseType(req.resourceType()), req.extraAttrs())));
            }
        }
        return ApiResponse.ok(DomainDtos.PermissionView.of(write.createPermission(req.code(),
                req.name() != null ? req.name() : req.code(), req.description(),
                parseType(req.resourceType()), req.extraAttrs(), req.externalKey())));
    }

    @Operation(summary = "更新权限")
    @PutMapping("/{id}")
    public ApiResponse<DomainDtos.PermissionView> update(@PathVariable String id, @RequestBody PermissionWriteRequest req) {
        OpenApiContext.requireScope("permission:write");
        return ApiResponse.ok(DomainDtos.PermissionView.of(write.updatePermission(id, req.name(), req.description(),
                parseType(req.resourceType()), req.extraAttrs())));
    }

    private static ResourceType parseType(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return ResourceType.valueOf(s.trim().toUpperCase());
    }
}
