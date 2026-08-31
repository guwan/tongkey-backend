package com.tongkey.console;

import com.tongkey.common.ApiResponse;
import com.tongkey.common.PageData;
import com.tongkey.domain.ResourceType;
import com.tongkey.domain.SourceType;
import com.tongkey.domain.service.DomainQueryService;
import com.tongkey.domain.service.DomainWriteService;
import io.swagger.v3.oas.annotations.Operation;
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

/**
 * 管理控制台：角色管理（含角色-权限绑定）。
 */
@Tag(name = "控制台-角色管理")
@RestController
@RequestMapping("/console/roles")
public class ConsoleRoleController {

    private final DomainQueryService query;
    private final DomainWriteService write;

    public ConsoleRoleController(DomainQueryService query, DomainWriteService write) {
        this.query = query;
        this.write = write;
    }

    @Operation(summary = "分页查询角色")
    @GetMapping
    public ApiResponse<PageData<DomainDtos.RoleView>> list(@RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) SourceType sourceType) {
        return ApiResponse.ok(PageData.map(query.pageRoles(page, size, keyword, sourceType), DomainDtos.RoleView::of));
    }

    @Operation(summary = "角色详情（含权限）")
    @GetMapping("/{id}")
    public ApiResponse<DomainDtos.RoleDetail> get(@PathVariable String id) {
        var r = DomainDtos.RoleView.of(write.requireRole(id));
        var perms = query.permissionsOfRole(id).stream().map(DomainDtos.PermissionView::of).toList();
        return ApiResponse.ok(new DomainDtos.RoleDetail(r, perms));
    }

    @Operation(summary = "新建角色")
    @PostMapping
    public ApiResponse<DomainDtos.RoleView> create(@RequestBody @jakarta.validation.Valid DomainDtos.RoleRequest req) {
        return ApiResponse.ok(DomainDtos.RoleView.of(write.createRole(req.code(), req.name(), req.description(), req.extraAttrs())));
    }

    @Operation(summary = "更新角色")
    @PutMapping("/{id}")
    public ApiResponse<DomainDtos.RoleView> update(@PathVariable String id, @RequestBody DomainDtos.RoleRequest req) {
        return ApiResponse.ok(DomainDtos.RoleView.of(write.updateRole(id, req.name(), req.description(), req.extraAttrs())));
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        write.deleteRole(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "角色绑定权限")
    @PostMapping("/{id}/permissions")
    public ApiResponse<DomainDtos.LinkView> bindPermission(@PathVariable String id, @RequestBody BindPermission req) {
        return ApiResponse.ok(DomainDtos.LinkView.of(write.bindRolePermission(id, req.permissionId())));
    }

    @Operation(summary = "角色解绑权限")
    @DeleteMapping("/{id}/permissions/{permissionId}")
    public ApiResponse<Void> unbindPermission(@PathVariable String id, @PathVariable String permissionId) {
        write.unbindRolePermission(id, permissionId);
        return ApiResponse.ok();
    }

    public record BindPermission(@NotBlank String permissionId) {
    }
}
