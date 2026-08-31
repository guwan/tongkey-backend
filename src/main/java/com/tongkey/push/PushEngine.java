package com.tongkey.push;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongkey.common.ApiException;
import com.tongkey.common.CryptoUtil;
import com.tongkey.common.ErrorCode;
import com.tongkey.domain.ChangeAction;
import com.tongkey.domain.EntityType;
import com.tongkey.domain.entity.PermissionEntity;
import com.tongkey.domain.entity.RoleEntity;
import com.tongkey.domain.entity.RolePermission;
import com.tongkey.domain.entity.UserEntity;
import com.tongkey.domain.entity.UserRole;
import com.tongkey.domain.repository.PermissionRepository;
import com.tongkey.domain.repository.RolePermissionRepository;
import com.tongkey.domain.repository.RoleRepository;
import com.tongkey.domain.repository.UserRepository;
import com.tongkey.domain.repository.UserRoleRepository;
import com.tongkey.domain.service.DomainWriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 推送引擎（规格文档第 6 章）：
 * <ul>
 *   <li>事件监听器在事务提交后投递推送任务，本引擎异步执行，避免阻塞主流程；</li>
 *   <li>失败按指数退避进入重试队列，超过最大次数标记失败，支持管理台手动重推；</li>
 *   <li>每次推送落 {@link PushLog}：请求体、响应状态码、响应内容、耗时。</li>
 * </ul>
 */
@Service
public class PushEngine {

    private static final Logger log = LoggerFactory.getLogger(PushEngine.class);

    private final PushTargetRepository targetRepository;
    private final PushLogRepository pushLogRepository;
    private final CryptoUtil crypto;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${tongkey.push.read-timeout-seconds:30}")
    private int readTimeoutSeconds;

    public PushEngine(PushTargetRepository targetRepository, PushLogRepository pushLogRepository, CryptoUtil crypto,
                      UserRepository userRepository, RoleRepository roleRepository,
                      PermissionRepository permissionRepository, UserRoleRepository userRoleRepository,
                      RolePermissionRepository rolePermissionRepository) {
        this.targetRepository = targetRepository;
        this.pushLogRepository = pushLogRepository;
        this.crypto = crypto;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRoleRepository = userRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    // ============================== 事件入口 ==============================

    /** 核心域变更 → 匹配推送目标并异步推送（规格文档 6.2 增量推送）。 */
    public void onChange(EntityType entityType, ChangeAction action, String entityId, Map<String, Object> snapshot) {
        TriggerEvent event = switch (action) {
            case CREATE -> TriggerEvent.ON_CREATE;
            case UPDATE -> TriggerEvent.ON_UPDATE;
            case DELETE -> TriggerEvent.ON_DELETE;
        };
        for (PushTarget target : targetRepository.findByEnabledTrue()) {
            if (!containsCsv(target.getTriggerEvents(), event.name())
                    || !containsCsv(target.getEntityScope(), entityType.name())) {
                continue;
            }
            PushLog pushLog = newLog(target, event, entityType, entityId);
            pushLog.setRequestBody(renderPayload(target, event, entityType, action, snapshot));
            pushLogRepository.save(pushLog);
            executeAsync(pushLog.getId());
        }
    }

    /** 初始化/全量推送（规格文档 6.2）：将当前全部符合 entity_scope 的数据分批推送。 */
    @Async
    public void fullPushAsync(String targetId) {
        fullPush(targetId);
    }

    public int fullPush(String targetId) {
        PushTarget target = targetRepository.findById(targetId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "推送目标不存在: " + targetId));
        if (!target.isEnabled()) {
            throw new ApiException(ErrorCode.INVALID_PARAM, "推送目标未启用");
        }
        int total = 0;
        for (String scope : target.getEntityScope().split(",")) {
            EntityType type = EntityType.valueOf(scope.trim());
            List<Map<String, Object>> snapshots = snapshotsOf(type);
            for (List<Map<String, Object>> batch : partition(snapshots, 100)) {
                PushLog pushLog = newLog(target, TriggerEvent.ON_INIT, type, null);
                pushLog.setRequestBody(renderBatchPayload(target, type, batch));
                pushLogRepository.save(pushLog);
                execute(pushLog.getId());
                total += batch.size();
            }
        }
        return total;
    }

    /** 管理台手动重推某条失败记录。 */
    public PushLog manualRetry(Long pushLogId) {
        PushLog pushLog = pushLogRepository.findById(pushLogId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "推送日志不存在"));
        pushLog.setStatus(PushLog.PushStatus.PENDING);
        pushLog.setRetryCount(0);
        pushLog.setNextRetryAt(null);
        pushLogRepository.save(pushLog);
        execute(pushLogId);
        return pushLogRepository.findById(pushLogId).orElse(pushLog);
    }

    // ============================== 执行 ==============================

    @Async
    public void executeAsync(Long pushLogId) {
        execute(pushLogId);
    }

    /** 定时扫描重试队列（指数退避到期项）。 */
    @Scheduled(fixedDelay = 5000)
    public void retryScan() {
        List<PushLog> due = pushLogRepository.findByStatusAndNextRetryAtLessThanEqual(
                PushLog.PushStatus.PENDING, Instant.now());
        for (PushLog pushLog : due) {
            execute(pushLog.getId());
        }
    }

    private void execute(Long pushLogId) {
        PushLog pushLog = pushLogRepository.findById(pushLogId).orElse(null);
        if (pushLog == null || pushLog.getStatus() == PushLog.PushStatus.SUCCESS) {
            return;
        }
        PushTarget target = targetRepository.findById(pushLog.getTargetId()).orElse(null);
        if (target == null) {
            pushLog.setStatus(PushLog.PushStatus.FAILED);
            pushLog.setErrorMessage("推送目标已被删除");
            pushLogRepository.save(pushLog);
            return;
        }
        long start = System.currentTimeMillis();
        try {
            HttpRequest request = buildRequest(target, pushLog.getRequestBody());
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            pushLog.setRequestUrl(target.getEndpointUrl());
            pushLog.setResponseStatus(response.statusCode());
            pushLog.setResponseBody(limitText(response.body(), 4000));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                pushLog.setStatus(PushLog.PushStatus.SUCCESS);
                pushLog.setNextRetryAt(null);
            } else {
                onFailure(target, pushLog, "HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            log.warn("推送失败: target={}, err={}", target.getName(), e.getMessage());
            onFailure(target, pushLog, e.getMessage());
        } finally {
            pushLog.setCostMs(System.currentTimeMillis() - start);
            pushLogRepository.save(pushLog);
        }
    }

    private void onFailure(PushTarget target, PushLog pushLog, String error) {
        pushLog.setErrorMessage(limitText(error, 2000));
        int retry = pushLog.getRetryCount() + 1;
        pushLog.setRetryCount(retry);
        if (retry > target.getRetryMax()) {
            pushLog.setStatus(PushLog.PushStatus.FAILED); // 超过最大重试次数，等待人工重推
            pushLog.setNextRetryAt(null);
        } else {
            pushLog.setStatus(PushLog.PushStatus.PENDING);
            long backoff = (long) target.getRetryIntervalSeconds() * (1L << (retry - 1)); // 指数退避
            pushLog.setNextRetryAt(Instant.now().plusSeconds(backoff));
        }
    }

    private HttpRequest buildRequest(PushTarget target, String body) {
        String safeBody = body != null ? body : "{}";
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(target.getEndpointUrl()))
                .timeout(Duration.ofSeconds(readTimeoutSeconds))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("X-TongKey-Event", "push")
                .method(target.getHttpMethod() != null ? target.getHttpMethod() : "POST",
                        HttpRequest.BodyPublishers.ofString(safeBody, StandardCharsets.UTF_8));
        applyAuth(builder, target, safeBody);
        return builder.build();
    }

    private void applyAuth(HttpRequest.Builder builder, PushTarget target, String body) {
        Map<String, String> cfg = parseAuthConfig(target.getAuthConfig());
        switch (target.getAuthType()) {
            case BASIC -> {
                String raw = cfg.getOrDefault("username", "") + ":" + cfg.getOrDefault("password", "");
                builder.header("Authorization", "Basic "
                        + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
            }
            case BEARER -> builder.header("Authorization", "Bearer " + cfg.getOrDefault("token", ""));
            case HMAC_SIGNATURE -> {
                String secret = cfg.getOrDefault("secretKey", "");
                String headerName = cfg.getOrDefault("headerName", "X-TongKey-Signature");
                long timestamp = System.currentTimeMillis();
                String sign = hmacSha256(secret, timestamp + "\n" + body);
                builder.header(headerName, sign);
                builder.header("X-TongKey-Timestamp", String.valueOf(timestamp));
            }
            case NONE -> {
                // 无鉴权
            }
        }
    }

    private Map<String, String> parseAuthConfig(String stored) {
        Map<String, String> out = new LinkedHashMap<>();
        if (stored == null || stored.isBlank()) {
            return out;
        }
        try {
            String json = crypto.decrypt(stored);
            Map<String, Object> m = objectMapper.readValue(json, new TypeReference<>() {
            });
            m.forEach((k, v) -> out.put(k, v == null ? "" : v.toString()));
        } catch (Exception e) {
            log.warn("解析推送鉴权配置失败: {}", e.getMessage());
        }
        return out;
    }

    public static String hmacSha256(String secret, String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    // ============================== 报文渲染 ==============================

    /** 按 payload_template 做字段映射；未配置模板则发送标准报文（供文档站点说明）。 */
    private String renderPayload(PushTarget target, TriggerEvent event, EntityType type,
                                 ChangeAction action, Map<String, Object> snapshot) {
        Map<String, Object> template = parseTemplate(target.getPayloadTemplate());
        Map<String, Object> payload = new LinkedHashMap<>();
        if (template.isEmpty() || snapshot == null) {
            payload.put("event", event.name());
            payload.put("action", action.name());
            payload.put("entityType", type.name());
            payload.put("entityId", snapshot != null ? snapshot.get("id") : null);
            payload.put("data", snapshot);
            payload.put("timestamp", Instant.now().toString());
        } else {
            template.forEach((targetField, sourceField) -> payload.put(targetField, snapshot.get(sourceField.toString())));
        }
        return toJson(payload);
    }

    private String renderBatchPayload(PushTarget target, EntityType type, List<Map<String, Object>> batch) {
        Map<String, Object> template = parseTemplate(target.getPayloadTemplate());
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> snapshot : batch) {
            if (template.isEmpty()) {
                items.add(snapshot);
            } else {
                Map<String, Object> item = new LinkedHashMap<>();
                template.forEach((tf, sf) -> item.put(tf, snapshot.get(sf.toString())));
                items.add(item);
            }
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", TriggerEvent.ON_INIT.name());
        payload.put("entityType", type.name());
        payload.put("total", items.size());
        payload.put("data", items);
        payload.put("timestamp", Instant.now().toString());
        return toJson(payload);
    }

    private Map<String, Object> parseTemplate(String templateJson) {
        if (templateJson == null || templateJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(templateJson, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("payload_template 非法，将使用标准报文: {}", e.getMessage());
            return Map.of();
        }
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    // ============================== 全量快照读取 ==============================

    private List<Map<String, Object>> snapshotsOf(EntityType type) {
        return switch (type) {
            case USER -> userRepository.findAll().stream().map(DomainWriteService::snapshot)
                    .collect(java.util.stream.Collectors.toList());
            case ROLE -> roleRepository.findAll().stream().map(DomainWriteService::snapshot).toList();
            case PERMISSION -> permissionRepository.findAll().stream().map(DomainWriteService::snapshot).toList();
            case USER_ROLE -> userRoleRepository.findAll(PageRequest.of(0, 100000)).getContent().stream()
                    .map(DomainWriteService::snapshot).toList();
            case ROLE_PERMISSION -> rolePermissionRepository.findAll(PageRequest.of(0, 100000)).getContent().stream()
                    .map(DomainWriteService::snapshot).toList();
        };
    }

    private PushLog newLog(PushTarget target, TriggerEvent event, EntityType type, String entityId) {
        PushLog pushLog = new PushLog();
        pushLog.setTargetId(target.getId());
        pushLog.setTargetName(target.getName());
        pushLog.setTriggerEvent(event);
        pushLog.setEntityType(type);
        pushLog.setEntityId(entityId);
        pushLog.setRequestUrl(target.getEndpointUrl());
        return pushLog;
    }

    private static boolean containsCsv(String csv, String value) {
        if (csv == null) {
            return false;
        }
        for (String part : csv.split(",")) {
            if (part.trim().equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private static <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            out.add(list.subList(i, Math.min(i + size, list.size())));
        }
        if (out.isEmpty()) {
            out.add(List.of());
        }
        return out;
    }

    private static String limitText(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
