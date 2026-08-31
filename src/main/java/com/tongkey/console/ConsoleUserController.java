package com.tongkey.console;

import com.tongkey.common.ApiResponse;
import com.tongkey.common.OperatorContext;
import com.tongkey.common.PageData;
import com.tongkey.domain.EntityStatus;
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
 * 管理控制台：用户管理（规格文档第 9 章）。
 */
@Tag(name = "控制台-用户管理")
@RestController
@RequestMapping("/console/users")
public class ConsoleUserController {

    private final DomainQueryService query;
    private final DomainWriteService write;

    public ConsoleUserController(DomainQueryService query, DomainWriteService write) {
        this.query = query;
        this.write = write;
    }

    @Operation(summary = "分页查询用户", description = "支持关键字（用户名/显示名）、来源类型、状态过滤")
    @GetMapping
    public ApiResponse<PageData<DomainDtos.UserView>> list(@RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) SourceType sourceType,
                                                           @RequestParam(required = false) EntityStatus status) {
        return ApiResponse.ok(PageData.map(query.pageUsers(page, size, keyword, sourceType, status), DomainDtos.UserView::of));
    }

    @Operation(summary = "用户详情（含角色）")
    @GetMapping("/{id}")
    public ApiResponse<DomainDtos.UserDetail> get(@PathVariable String id) {
        var u = DomainDtos.UserView.of(write.requireUser(id));
        var roles = query.rolesOfUser(id).stream().map(DomainDtos.RoleView::of).toList();
        return ApiResponse.ok(new DomainDtos.UserDetail(u, roles));
    }

    @Operation(summary = "新建用户（原生数据）")
    @PostMapping
    public ApiResponse<DomainDtos.UserView> create(@RequestBody @jakarta.validation.Valid UserCreate req) {
        OperatorContext.set(OperatorContext.operator(), OperatorContext.CHANNEL_CONSOLE);
        return ApiResponse.ok(DomainDtos.UserView.of(write.createUser(req.username(), req.displayName(),
                parseStatus(req.status()), req.extraAttrs())));
    }

    @Operation(summary = "更新用户")
    @PutMapping("/{id}")
    public ApiResponse<DomainDtos.UserView> update(@PathVariable String id, @RequestBody DomainDtos.UserRequest req) {
        return ApiResponse.ok(DomainDtos.UserView.of(write.updateUser(id, req.displayName(),
                parseStatus(req.status()), req.extraAttrs())));
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        write.deleteUser(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "用户绑定角色")
    @PostMapping("/{id}/roles")
    public ApiResponse<DomainDtos.LinkView> bindRole(@PathVariable String id, @RequestBody BindRole req) {
        return ApiResponse.ok(DomainDtos.LinkView.of(write.bindUserRole(id, req.roleId())));
    }

    @Operation(summary = "用户解绑角色")
    @DeleteMapping("/{id}/roles/{roleId}")
    public ApiResponse<Void> unbindRole(@PathVariable String id, @PathVariable String roleId) {
        write.unbindUserRole(id, roleId);
        return ApiResponse.ok();
    }

    public record UserCreate(@NotBlank String username, String displayName, String status, String extraAttrs) {
    }

    public record BindRole(@NotBlank String roleId) {
    }

    private static EntityStatus parseStatus(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return EntityStatus.valueOf(s.trim().toUpperCase());
    }
}
