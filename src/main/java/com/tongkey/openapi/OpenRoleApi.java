package com.tongkey.openapi;

import com.tongkey.common.ApiResponse;
import com.tongkey.common.PageData;
import com.tongkey.console.DomainDtos;
import com.tongkey.domain.SourceType;
import com.tongkey.domain.repository.RoleRepository;
import com.tongkey.domain.service.DomainQueryService;
import com.tongkey.domain.service.DomainWriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
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

/**
 * 开放 API：角色（含角色-权限绑定）。
 */
@Tag(name = "开放API-角色", description = "供第三方系统查询/创建/更新角色，需 X-API-Key 鉴权")
@SecurityRequirement(name = "ApiKeyAuth")
@RestController
@RequestMapping("/api/v1/roles")
public class OpenRoleApi {

    private final DomainQueryService query;
    private final DomainWriteService write;
    private final RoleRepository roleRepository;

    public OpenRoleApi(DomainQueryService query, DomainWriteService write, RoleRepository roleRepository) {
        this.query = query;
        this.write = write;
        this.roleRepository = roleRepository;
    }

    public record RoleWriteRequest(@NotBlank String code, String name, String description,
                                   String extraAttrs, String externalKey) {
    }

    @Operation(summary = "分页查询角色")
    @GetMapping
    public ApiResponse<PageData<DomainDtos.RoleView>> list(@RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) SourceType sourceType) {
        OpenApiContext.requireScope("role:read");
        return ApiResponse.ok(PageData.map(query.pageRoles(page, size, keyword, sourceType), DomainDtos.RoleView::of));
    }

    @Operation(summary = "角色详情")
    @GetMapping("/{id}")
    public ApiResponse<DomainDtos.RoleView> get(@PathVariable String id) {
        OpenApiContext.requireScope("role:read");
        return ApiResponse.ok(DomainDtos.RoleView.of(write.requireRole(id)));
    }

    @Operation(summary = "创建角色", description = "携带 externalKey 时具备幂等性")
    @PostMapping
    public ApiResponse<DomainDtos.RoleView> create(@RequestBody @jakarta.validation.Valid RoleWriteRequest req) {
        OpenApiContext.requireScope("role:write");
        if (req.externalKey() != null) {
            var existing = roleRepository.findFirstByExternalKey(req.externalKey());
            if (existing.isPresent()) {
                return ApiResponse.ok(DomainDtos.RoleView.of(
                        write.updateRole(existing.get().getId(), req.name(), req.description(), req.extraAttrs())));
            }
        }
        return ApiResponse.ok(DomainDtos.RoleView.of(
                write.createRole(req.code(), req.name() != null ? req.name() : req.code(), req.description(), req.extraAttrs(), req.externalKey())));
    }

    @Operation(summary = "更新角色")
    @PutMapping("/{id}")
    public ApiResponse<DomainDtos.RoleView> update(@PathVariable String id, @RequestBody RoleWriteRequest req) {
        OpenApiContext.requireScope("role:write");
        return ApiResponse.ok(DomainDtos.RoleView.of(
                write.updateRole(id, req.name(), req.description(), req.extraAttrs())));
    }

    @Operation(summary = "角色的权限列表")
    @GetMapping("/{id}/permissions")
    public ApiResponse<List<DomainDtos.PermissionView>> permissionsOf(@PathVariable String id) {
        OpenApiContext.requireScope("role:read");
        write.requireRole(id);
        return ApiResponse.ok(query.permissionsOfRole(id).stream().map(DomainDtos.PermissionView::of).toList());
    }

    @Operation(summary = "角色绑定权限")
    @PostMapping("/{id}/permissions/{permissionId}")
    public ApiResponse<DomainDtos.LinkView> bind(@PathVariable String id, @PathVariable String permissionId) {
        OpenApiContext.requireScope("role_permission:write");
        return ApiResponse.ok(DomainDtos.LinkView.of(write.bindRolePermission(id, permissionId)));
    }

    @Operation(summary = "角色解绑权限")
    @DeleteMapping("/{id}/permissions/{permissionId}")
    public ApiResponse<Void> unbind(@PathVariable String id, @PathVariable String permissionId) {
        OpenApiContext.requireScope("role_permission:write");
        write.unbindRolePermission(id, permissionId);
        return ApiResponse.ok();
    }
}
