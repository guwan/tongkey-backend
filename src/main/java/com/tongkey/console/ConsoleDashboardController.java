package com.tongkey.console;

import com.tongkey.common.ApiResponse;
import com.tongkey.domain.SourceType;
import com.tongkey.domain.repository.PermissionRepository;
import com.tongkey.domain.repository.RoleRepository;
import com.tongkey.domain.repository.UserRepository;
import com.tongkey.push.PushLog;
import com.tongkey.push.PushLogRepository;
import com.tongkey.push.PushTargetRepository;
import com.tongkey.datasource.DataSourceRepository;
import com.tongkey.sync.SyncLog;
import com.tongkey.sync.SyncLogRepository;
import com.tongkey.sync.SyncMappingRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理控制台：仪表盘（规格文档第 9 章）——数据总量、近期同步/推送成功率。
 */
@Tag(name = "控制台-仪表盘")
@RestController
@RequestMapping("/console/dashboard")
public class ConsoleDashboardController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final DataSourceRepository dataSourceRepository;
    private final SyncMappingRepository syncMappingRepository;
    private final SyncLogRepository syncLogRepository;
    private final PushTargetRepository pushTargetRepository;
    private final PushLogRepository pushLogRepository;

    public ConsoleDashboardController(UserRepository userRepository, RoleRepository roleRepository,
                                      PermissionRepository permissionRepository, DataSourceRepository dataSourceRepository,
                                      SyncMappingRepository syncMappingRepository, SyncLogRepository syncLogRepository,
                                      PushTargetRepository pushTargetRepository, PushLogRepository pushLogRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.syncMappingRepository = syncMappingRepository;
        this.syncLogRepository = syncLogRepository;
        this.pushTargetRepository = pushTargetRepository;
        this.pushLogRepository = pushLogRepository;
    }

    @Operation(summary = "仪表盘统计数据")
    @GetMapping
    public ApiResponse<Map<String, Object>> dashboard() {
        Instant last7d = Instant.now().minus(7, ChronoUnit.DAYS);
        Map<String, Object> data = new LinkedHashMap<>();

        Map<String, Object> domain = new LinkedHashMap<>();
        domain.put("users", userRepository.count());
        domain.put("roles", roleRepository.count());
        domain.put("permissions", permissionRepository.count());
        domain.put("nativeUsers", userRepository.countBySourceType(SourceType.NATIVE));
        domain.put("syncedUsers", userRepository.countBySourceType(SourceType.SYNCED));
        domain.put("apiUsers", userRepository.countBySourceType(SourceType.API));
        data.put("domain", domain);

        data.put("datasources", dataSourceRepository.count());
        data.put("syncMappings", syncMappingRepository.count());
        data.put("pushTargets", pushTargetRepository.count());

        long syncOk7d = syncLogRepository.countByStatusAndStartedAtAfter(SyncLog.SyncStatus.SUCCESS, last7d);
        long syncFail7d = syncLogRepository.countByStatusAndStartedAtAfter(SyncLog.SyncStatus.FAILED, last7d);
        Map<String, Object> sync = new LinkedHashMap<>();
        sync.put("success7d", syncOk7d);
        sync.put("failed7d", syncFail7d);
        sync.put("successRate7d", rate(syncOk7d, syncFail7d));
        data.put("sync", sync);

        long pushOk7d = pushLogRepository.countByStatusAndCreatedAtAfter(PushLog.PushStatus.SUCCESS, last7d);
        long pushFail7d = pushLogRepository.countByStatusAndCreatedAtAfter(PushLog.PushStatus.FAILED, last7d);
        long pushPending7d = pushLogRepository.countByStatusAndCreatedAtAfter(PushLog.PushStatus.PENDING, last7d);
        Map<String, Object> push = new LinkedHashMap<>();
        push.put("success7d", pushOk7d);
        push.put("failed7d", pushFail7d);
        push.put("pending7d", pushPending7d);
        push.put("successRate7d", rate(pushOk7d, pushFail7d));
        data.put("push", push);

        data.put("serverTimeMillis", System.currentTimeMillis());
        return ApiResponse.ok(data);
    }

    private static double rate(long ok, long fail) {
        long total = ok + fail;
        return total == 0 ? 100.0 : Math.round(ok * 10000.0 / total) / 100.0;
    }
}
