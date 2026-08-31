package com.tongkey.sync;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongkey.common.ApiException;
import com.tongkey.common.ErrorCode;
import com.tongkey.common.OperatorContext;
import com.tongkey.datasource.DataSourceConfig;
import com.tongkey.datasource.DataSourceRepository;
import com.tongkey.datasource.RemoteDataSourceManager;
import com.tongkey.datasource.SyncMode;
import com.tongkey.datasource.dialect.DbDialectFactory;
import com.tongkey.domain.ChangeAction;
import com.tongkey.domain.EntityType;
import com.tongkey.domain.SourceType;
import com.tongkey.domain.entity.PermissionEntity;
import com.tongkey.domain.entity.RoleEntity;
import com.tongkey.domain.entity.UserEntity;
import com.tongkey.domain.repository.PermissionRepository;
import com.tongkey.domain.repository.RoleRepository;
import com.tongkey.domain.repository.UserRepository;
import com.tongkey.domain.service.DomainWriteService;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 同步引擎（规格文档 5.3）：
 * <ol>
 *   <li>调度/手动触发；</li>
 *   <li>动态复用 JDBC 连接，执行配置化 SQL（增量模式绑定 :lastSyncTime 水位）；</li>
 *   <li>按 field_mapping 映射结果列 → 实体字段；</li>
 *   <li>按 external_key upsert，冲突策略生效；</li>
 *   <li>记录同步日志；全量模式下对源端已消失的同步数据做软禁用。</li>
 * </ol>
 * 同步产生的写入走统一域写路径，自动触发领域事件（可联动推送引擎）与审计记录。
 */
@Service
public class SyncEngine {

    private static final Logger log = LoggerFactory.getLogger(SyncEngine.class);
    public static final String LAST_SYNC_PARAM = "lastSyncTime";

    private final DataSourceRepository dataSourceRepository;
    private final SyncMappingRepository mappingRepository;
    private final SyncLogRepository syncLogRepository;
    private final RemoteDataSourceManager poolManager;
    private final DomainWriteService writeService;
    private final SqlReadonlyValidator sqlValidator;
    private final DbDialectFactory dialectFactory;
    private final TransactionTemplate txTemplate;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${tongkey.sync.query-timeout-seconds:300}")
    private int queryTimeoutSeconds;

    public SyncEngine(DataSourceRepository dataSourceRepository, SyncMappingRepository mappingRepository,
                      SyncLogRepository syncLogRepository, RemoteDataSourceManager poolManager,
                      DomainWriteService writeService, SqlReadonlyValidator sqlValidator,
                      DbDialectFactory dialectFactory, TransactionTemplate txTemplate,
                      UserRepository userRepository, RoleRepository roleRepository,
                      PermissionRepository permissionRepository) {
        this.dataSourceRepository = dataSourceRepository;
        this.mappingRepository = mappingRepository;
        this.syncLogRepository = syncLogRepository;
        this.poolManager = poolManager;
        this.writeService = writeService;
        this.sqlValidator = sqlValidator;
        this.dialectFactory = dialectFactory;
        this.txTemplate = txTemplate;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    /** 执行数据源下全部启用的映射任务。 */
    public List<SyncLog> runDataSource(String dataSourceId, SyncLog.SyncTrigger trigger) {
        List<SyncLog> logs = new ArrayList<>();
        for (SyncMapping m : mappingRepository.findByDataSourceIdAndEnabledTrue(dataSourceId)) {
            logs.add(runMapping(m.getId(), trigger));
        }
        return logs;
    }

    public SyncLog runMapping(String mappingId, SyncLog.SyncTrigger trigger) {
        SyncMapping mapping = mappingRepository.findById(mappingId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "映射配置不存在: " + mappingId));
        DataSourceConfig ds = dataSourceRepository.findById(mapping.getDataSourceId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "数据源不存在: " + mapping.getDataSourceId()));

        SyncLog syncLog = new SyncLog();
        syncLog.setMappingId(mapping.getId());
        syncLog.setDataSourceId(ds.getId());
        syncLog.setMappingName(mapping.getName());
        syncLog.setTrigger(trigger);
        syncLog = syncLogRepository.save(syncLog);

        long start = System.currentTimeMillis();
        // 操作者上下文：渠道 "SYNC:数据源名"
        OperatorContext.set("", "SYNC:" + ds.getName());
        try {
            execute(mapping, ds, syncLog);
            syncLog.setStatus(SyncLog.SyncStatus.SUCCESS);
        } catch (Exception e) {
            log.error("同步失败: mapping={}, ds={}", mapping.getName(), ds.getName(), e);
            syncLog.setStatus(SyncLog.SyncStatus.FAILED);
            syncLog.setErrorDetail(limitText(e.getMessage(), 4000));
        } finally {
            OperatorContext.clear();
            syncLog.setFinishedAt(Instant.now());
            syncLog.setDurationMs(System.currentTimeMillis() - start);
            syncLogRepository.save(syncLog);
        }
        return syncLog;
    }

    private void execute(SyncMapping mapping, DataSourceConfig ds, SyncLog syncLog) {
        String baseSql = sqlValidator.validate(mapping.getSqlText());
        Map<String, String> fieldMapping = parseFieldMapping(mapping.getFieldMapping());
        boolean incremental = ds.getSyncMode() == SyncMode.INCREMENTAL;
        if (incremental && (ds.getIncrementalColumn() == null || ds.getIncrementalColumn().isBlank())
                && !baseSql.contains(":" + LAST_SYNC_PARAM)) {
            throw new ApiException(ErrorCode.INVALID_PARAM, "增量模式需配置增量字段或在 SQL 中使用 :" + LAST_SYNC_PARAM + " 占位符");
        }

        NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(poolManager.pool(ds));
        template.getJdbcTemplate().setQueryTimeout(queryTimeoutSeconds);

        // 组装增量过滤（统一走绑定变量防注入，规格文档 5.1.1）
        String sql = baseSql;
        MapSqlParameterSource params = new MapSqlParameterSource();
        Object watermark = parseWatermark(mapping.getLastSyncValue());
        if (incremental) {
            Object bindValue = watermark != null ? watermark : new Timestamp(0L);
            if (sql.contains(":" + LAST_SYNC_PARAM)) {
                params.addValue(LAST_SYNC_PARAM, bindValue);
            } else if (watermark != null) {
                sql = "SELECT * FROM (" + sql + ") tk_incr WHERE " + ds.getIncrementalColumn() + " > :" + LAST_SYNC_PARAM;
                params.addValue(LAST_SYNC_PARAM, bindValue);
            }
        }

        // 先计算本次集合的水位（用于成功后更新），基于同一过滤集合
        String watermarkCol = ds.getIncrementalColumn();
        Object newWatermark = null;
        if (incremental && watermarkCol != null && !watermarkCol.isBlank()) {
            try {
                newWatermark = template.queryForObject(
                        "SELECT MAX(" + watermarkCol + ") FROM (" + sql + ") tk_wm", params, Object.class);
            } catch (Exception e) {
                log.warn("计算增量水位失败，本次同步后不更新水位: {}", e.getMessage());
            }
        }

        int batchSize = mapping.getBatchSize() > 0 ? mapping.getBatchSize() : 500;
        Set<String> seenExternalKeys = new HashSet<>();
        List<Map<String, Object>> batch = new ArrayList<>(batchSize);
        long[] counters = new long[4]; // inserted, updated, skipped, failed

        template.query(sql, params, rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            int n = rs.getMetaData().getColumnCount();
            for (int i = 1; i <= n; i++) {
                row.put(rs.getMetaData().getColumnLabel(i), rs.getObject(i));
            }
            batch.add(row);
            if (batch.size() >= batchSize) {
                flushBatch(mapping, ds, fieldMapping, batch, seenExternalKeys, counters);
                batch.clear();
            }
        });
        if (!batch.isEmpty()) {
            flushBatch(mapping, ds, fieldMapping, batch, seenExternalKeys, counters);
            batch.clear();
        }

        // 全量模式：源端消失的同步数据软禁用（仅 USER 有状态字段）
        if (!incremental) {
            softDisableMissing(mapping, ds, seenExternalKeys);
        }

        syncLog.setInsertedCount(counters[0]);
        syncLog.setUpdatedCount(counters[1]);
        syncLog.setSkippedCount(counters[2]);
        syncLog.setFailedCount(counters[3]);

        // 成功后更新增量水位
        if (incremental && newWatermark != null) {
            mapping.setLastSyncValue(newWatermark.toString());
            mappingRepository.save(mapping);
        }
    }

    private void flushBatch(SyncMapping mapping, DataSourceConfig ds, Map<String, String> fieldMapping,
                            List<Map<String, Object>> rows, Set<String> seenExternalKeys, long[] counters) {
        txTemplate.executeWithoutResult(status -> {
            for (Map<String, Object> row : rows) {
                try {
                    Map<String, Object> fields = applyFieldMapping(fieldMapping, row);
                    String externalKey = str(fields.get("external_key"));
                    if (externalKey == null) {
                        counters[3]++;
                        continue;
                    }
                    seenExternalKeys.add(externalKey);
                    ChangeAction action = switch (mapping.getTargetEntity()) {
                        case USER -> writeService.upsertUserFromSync(ds.getId(), externalKey, fields, mapping.getConflictStrategy());
                        case ROLE -> writeService.upsertRoleFromSync(ds.getId(), externalKey, fields, mapping.getConflictStrategy());
                        case PERMISSION -> writeService.upsertPermissionFromSync(ds.getId(), externalKey, fields, mapping.getConflictStrategy());
                        case USER_ROLE -> upsertUserRole(mapping, ds, fields, externalKey);
                        case ROLE_PERMISSION -> upsertRolePermission(mapping, ds, fields, externalKey);
                    };
                    if (action == ChangeAction.CREATE) {
                        counters[0]++;
                    } else if (action == ChangeAction.UPDATE) {
                        counters[1]++;
                    } else {
                        counters[2]++;
                    }
                } catch (Exception e) {
                    log.warn("同步行处理失败: mapping={}, err={}", mapping.getName(), e.getMessage());
                    counters[3]++;
                }
            }
        });
    }

    private ChangeAction upsertUserRole(SyncMapping mapping, DataSourceConfig ds, Map<String, Object> fields, String externalKey) {
        UserEntity user = resolveUser(ds.getId(), str(fields.get("user_external_key")));
        RoleEntity role = resolveRole(ds.getId(), str(fields.get("role_external_key")));
        if (user == null || role == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "用户/角色未同步到位，无法建立关联");
        }
        return writeService.upsertUserRoleFromSync(ds.getId(), externalKey, user.getId(), role.getId());
    }

    private ChangeAction upsertRolePermission(SyncMapping mapping, DataSourceConfig ds, Map<String, Object> fields, String externalKey) {
        RoleEntity role = resolveRole(ds.getId(), str(fields.get("role_external_key")));
        PermissionEntity permission = resolvePermission(ds.getId(), str(fields.get("permission_external_key")));
        if (role == null || permission == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "角色/权限未同步到位，无法建立关联");
        }
        return writeService.upsertRolePermissionFromSync(ds.getId(), externalKey, role.getId(), permission.getId());
    }

    private UserEntity resolveUser(String sourceId, String externalKey) {
        if (externalKey == null) {
            return null;
        }
        return userRepository.findBySourceIdAndExternalKey(sourceId, externalKey)
                .or(() -> userRepository.findByUsername(externalKey))
                .orElse(null);
    }

    private RoleEntity resolveRole(String sourceId, String externalKey) {
        if (externalKey == null) {
            return null;
        }
        return roleRepository.findBySourceIdAndExternalKey(sourceId, externalKey)
                .or(() -> roleRepository.findByCode(externalKey))
                .orElse(null);
    }

    private PermissionEntity resolvePermission(String sourceId, String externalKey) {
        if (externalKey == null) {
            return null;
        }
        return permissionRepository.findBySourceIdAndExternalKey(sourceId, externalKey)
                .or(() -> permissionRepository.findByCode(externalKey))
                .orElse(null);
    }

    /** 全量覆盖语义：本次结果集中未出现的同步数据（用户）标记为禁用。 */
    private void softDisableMissing(SyncMapping mapping, DataSourceConfig ds, Set<String> seen) {
        if (mapping.getTargetEntity() != EntityType.USER) {
            return;
        }
        Specification<UserEntity> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("sourceId"), ds.getId()));
            ps.add(cb.equal(root.get("sourceType"), SourceType.SYNCED));
            if (!seen.isEmpty()) {
                ps.add(root.get("externalKey").in(seen).not());
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
        txTemplate.executeWithoutResult(status -> userRepository.findAll(spec).forEach(u -> {
            if (u.getStatus() == com.tongkey.domain.EntityStatus.ENABLED) {
                writeService.updateUser(u.getId(), null, com.tongkey.domain.EntityStatus.DISABLED, null);
            }
        }));
    }

    /** SQL 结果列 → 目标字段；未映射的列自动收集进 extra_attrs。 */
    private Map<String, Object> applyFieldMapping(Map<String, String> fieldMapping, Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        Set<String> mappedColumns = new HashSet<>(fieldMapping.values());
        for (Map.Entry<String, String> e : fieldMapping.entrySet()) {
            out.put(e.getKey(), row.get(e.getValue()));
        }
        if (!out.containsKey("extra_attrs")) {
            Map<String, Object> extras = new LinkedHashMap<>();
            row.forEach((k, v) -> {
                if (!mappedColumns.contains(k)) {
                    extras.put(k, v);
                }
            });
            if (!extras.isEmpty()) {
                try {
                    out.put("extra_attrs", objectMapper.writeValueAsString(extras));
                } catch (Exception ignore) {
                    // 忽略无法序列化的扩展列
                }
            }
        }
        return out;
    }

    private Map<String, String> parseFieldMapping(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INVALID_PARAM, "field_mapping 不是合法 JSON: " + e.getMessage());
        }
    }

    /** 水位字符串 → 绑定值（时间戳/数字/字符串自适应）。 */
    private static Object parseWatermark(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Timestamp.valueOf(s);
        } catch (IllegalArgumentException ignore) {
            // not a timestamp
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ignore) {
            // not a number
        }
        return s;
    }

    /**
     * SQL 在线调试预览（规格文档第 9 章）：只读校验 + 方言限行包装 + 实际参数回显。
     */
    public Map<String, Object> preview(String dataSourceId, String sql, Integer limit) {
        DataSourceConfig ds = dataSourceRepository.findById(dataSourceId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "数据源不存在"));
        String validated = sqlValidator.validate(sql);
        int rowLimit = limit != null && limit > 0 ? Math.min(limit, 100) : 20;
        String previewSql = dialectFactory.require(ds.getDbType()).wrapLimit(validated, rowLimit);

        NamedParameterJdbcTemplate template = new NamedParameterJdbcTemplate(poolManager.pool(ds));
        template.getJdbcTemplate().setQueryTimeout(Math.min(queryTimeoutSeconds, 60));
        MapSqlParameterSource params = new MapSqlParameterSource();
        String displaySql = previewSql;
        if (previewSql.contains(":" + LAST_SYNC_PARAM)) {
            // 预览默认看全量：绑定极早时间戳；同时给出拼参后的语句方便核对（规格文档 5.1.1）
            Timestamp t = new Timestamp(0L);
            params.addValue(LAST_SYNC_PARAM, t);
            displaySql = previewSql.replace(":" + LAST_SYNC_PARAM, "'" + t + "'");
        }
        long start = System.currentTimeMillis();
        // displaySql 已将占位符拼为字面量，直接执行，方便与源库工具（如 Navicat）核对语句（规格文档 5.1.1）
        List<Map<String, Object>> rows = template.queryForList(displaySql, new MapSqlParameterSource());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executedSql", displaySql);
        result.put("rowCount", rows.size());
        result.put("costMs", System.currentTimeMillis() - start);
        result.put("rows", rows.stream().map(SyncEngine::stringifyRow).toList());
        return result;
    }

    private static Map<String, Object> stringifyRow(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        row.forEach((k, v) -> m.put(k, v == null ? null : v.toString()));
        return m;
    }

    private static String str(Object o) {
        if (o == null) {
            return null;
        }
        String s = o.toString();
        return s.isBlank() ? null : s;
    }

    private static String limitText(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
