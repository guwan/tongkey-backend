package com.tongkey.openapi;

import com.tongkey.common.ApiResponse;
import com.tongkey.common.PageData;
import com.tongkey.console.DomainDtos;
import com.tongkey.domain.EntityStatus;
import com.tongkey.domain.SourceType;
import com.tongkey.domain.entity.UserEntity;
import com.tongkey.domain.repository.UserRepository;
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

import java.util.ArrayList;
import java.util.List;

/**
 * 开放 API：用户（规格文档 7.1）。
 * <p>鉴权：请求头 {@code X-API-Key}；写入的数据自动标记 {@code source_type=API} 并记录调用方。
 * 写接口支持 {@code externalKey} 幂等：相同 externalKey 重复提交转为更新而非重复创建。</p>
 */
@Tag(name = "开放API-用户", description = "供第三方系统查询/创建/更新用户，需 X-API-Key 鉴权")
@SecurityRequirement(name = "ApiKeyAuth")
@RestController
@RequestMapping("/api/v1/users")
public class OpenUserApi {

    private final DomainQueryService query;
    private final DomainWriteService write;
    private final UserRepository userRepository;

    public OpenUserApi(DomainQueryService query, DomainWriteService write, UserRepository userRepository) {
        this.query = query;
        this.write = write;
        this.userRepository = userRepository;
    }

    public record UserWriteRequest(@NotBlank String username, String displayName, String status,
                                   String extraAttrs, String externalKey) {
    }

    @Operation(summary = "分页查询用户", description = "支持 page/size/keyword/sourceType/status 过滤")
    @GetMapping
    public ApiResponse<PageData<DomainDtos.UserView>> list(@RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) SourceType sourceType,
                                                           @RequestParam(required = false) EntityStatus status) {
        OpenApiContext.requireScope("user:read");
        return ApiResponse.ok(PageData.map(query.pageUsers(page, size, keyword, sourceType, status), DomainDtos.UserView::of));
    }

    @Operation(summary = "用户详情")
    @GetMapping("/{id}")
    public ApiResponse<DomainDtos.UserView> get(@PathVariable String id) {
        OpenApiContext.requireScope("user:read");
        return ApiResponse.ok(DomainDtos.UserView.of(write.requireUser(id)));
    }

    @Operation(summary = "创建用户", description = "携带 externalKey 时具备幂等性：已存在相同 externalKey 则更新")
    @PostMapping
    public ApiResponse<DomainDtos.UserView> create(@RequestBody @jakarta.validation.Valid UserWriteRequest req) {
        OpenApiContext.requireScope("user:write");
        if (req.externalKey() != null) {
            var existing = userRepository.findFirstByExternalKey(req.externalKey());
            if (existing.isPresent()) {
                return ApiResponse.ok(DomainDtos.UserView.of(
                        write.updateUser(existing.get().getId(), req.displayName(), parseStatus(req.status()), req.extraAttrs())));
            }
        }
        return ApiResponse.ok(DomainDtos.UserView.of(
                write.createUser(req.username(), req.displayName(), parseStatus(req.status()), req.extraAttrs(), req.externalKey())));
    }

    @Operation(summary = "更新用户")
    @PutMapping("/{id}")
    public ApiResponse<DomainDtos.UserView> update(@PathVariable String id, @RequestBody UserWriteRequest req) {
        OpenApiContext.requireScope("user:write");
        return ApiResponse.ok(DomainDtos.UserView.of(
                write.updateUser(id, req.displayName(), parseStatus(req.status()), req.extraAttrs())));
    }

    @Operation(summary = "批量创建/更新用户", description = "供初始化场景；按 externalKey 幂等 upsert")
    @PostMapping("/batch")
    public ApiResponse<List<DomainDtos.UserView>> batch(@RequestBody List<UserWriteRequest> items) {
        OpenApiContext.requireScope("user:write");
        List<DomainDtos.UserView> result = new ArrayList<>();
        for (UserWriteRequest req : items) {
            UserEntity u;
            if (req.externalKey() != null) {
                var existing = userRepository.findFirstByExternalKey(req.externalKey());
                if (existing.isPresent()) {
                    u = write.updateUser(existing.get().getId(), req.displayName(), parseStatus(req.status()), req.extraAttrs());
                } else {
                    u = write.createUser(req.username(), req.displayName(), parseStatus(req.status()), req.extraAttrs(), req.externalKey());
                }
            } else {
                u = write.createUser(req.username(), req.displayName(), parseStatus(req.status()), req.extraAttrs());
            }
            result.add(DomainDtos.UserView.of(u));
        }
        return ApiResponse.ok(result);
    }

    @Operation(summary = "用户的角色列表")
    @GetMapping("/{id}/roles")
    public ApiResponse<List<DomainDtos.RoleView>> rolesOf(@PathVariable String id) {
        OpenApiContext.requireScope("user:read");
        write.requireUser(id);
        return ApiResponse.ok(query.rolesOfUser(id).stream().map(DomainDtos.RoleView::of).toList());
    }

    @Operation(summary = "用户绑定角色")
    @PostMapping("/{id}/roles/{roleId}")
    public ApiResponse<DomainDtos.LinkView> bind(@PathVariable String id, @PathVariable String roleId) {
        OpenApiContext.requireScope("user_role:write");
        return ApiResponse.ok(DomainDtos.LinkView.of(write.bindUserRole(id, roleId)));
    }

    @Operation(summary = "用户解绑角色")
    @DeleteMapping("/{id}/roles/{roleId}")
    public ApiResponse<Void> unbind(@PathVariable String id, @PathVariable String roleId) {
        OpenApiContext.requireScope("user_role:write");
        write.unbindUserRole(id, roleId);
        return ApiResponse.ok();
    }

    private static EntityStatus parseStatus(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return EntityStatus.valueOf(s.trim().toUpperCase());
    }
}
