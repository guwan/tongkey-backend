package com.tongkey.sync;

import com.tongkey.datasource.DataSourceConfig;
import com.tongkey.datasource.DataSourceRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

/**
 * 双层定时调度：
 * <ul>
 *   <li>映射级 cron（优先级更高）：每个映射独立注册定时任务，精准控制单任务节奏</li>
 *   <li>数据源级 cron（批量入口）：触发该数据源下所有 enabled 映射，方便一键全量跑</li>
 * </ul>
 * <p>配置变更后调用 {@link #refresh()} 重新注册全部任务。</p>
 */
@Component
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

    private final DataSourceRepository dataSourceRepository;
    private final SyncMappingRepository mappingRepository;
    private final SyncEngine syncEngine;
    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    /** key = "MAP:" + mappingId 或 "DS:" + dataSourceId */
    private final Map<String, ScheduledFuture<?>> tasks = new HashMap<>();

    public SyncScheduler(DataSourceRepository dataSourceRepository,
                         SyncMappingRepository mappingRepository,
                         SyncEngine syncEngine) {
        this.dataSourceRepository = dataSourceRepository;
        this.mappingRepository = mappingRepository;
        this.syncEngine = syncEngine;
    }

    @PostConstruct
    void init() {
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("tk-sync-");
        scheduler.setDaemon(true);
        scheduler.initialize();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        refresh();
    }

    /** 取消全部任务并按最新配置重新注册。 */
    public synchronized void refresh() {
        tasks.values().forEach(f -> f.cancel(false));
        tasks.clear();

        // 1. 映射级 cron（精准调度）
        for (SyncMapping m : mappingRepository.findByEnabledTrue()) {
            String cron = m.getScheduleCron();
            if (cron == null || cron.isBlank()) continue;
            try {
                ScheduledFuture<?> future = scheduler.schedule(
                        () -> safeRunMapping(m.getId()),
                        new CronTrigger(cron));
                tasks.put("MAP:" + m.getId(), future);
                log.info("已注册映射级定时: {} [{}] cron={}", m.getName(), m.getId(), cron);
            } catch (IllegalArgumentException e) {
                log.error("映射 {} 的 cron 表达式无效: {}", m.getName(), cron);
            }
        }

        // 2. 数据源级 cron（批量入口，仅该数据源下没有映射级 cron 时才注册，避免重复执行）
        for (DataSourceConfig ds : dataSourceRepository.findByEnabledTrue()) {
            String cron = ds.getScheduleCron();
            if (cron == null || cron.isBlank()) continue;
            // 检查该数据源下是否有任何映射配置了独立 cron
            boolean hasMappingCron = mappingRepository.findByDataSourceIdAndEnabledTrue(ds.getId()).stream()
                    .anyMatch(m -> m.getScheduleCron() != null && !m.getScheduleCron().isBlank());
            if (hasMappingCron) {
                log.debug("数据源 {} 下存在映射级 cron，跳过数据源级批量调度", ds.getName());
                continue;
            }
            try {
                ScheduledFuture<?> future = scheduler.schedule(
                        () -> safeRunDataSource(ds.getId()),
                        new CronTrigger(cron));
                tasks.put("DS:" + ds.getId(), future);
                log.info("已注册数据源级定时: {} [{}] cron={}", ds.getName(), ds.getId(), cron);
            } catch (IllegalArgumentException e) {
                log.error("数据源 {} 的 cron 表达式无效: {}", ds.getName(), cron);
            }
        }
    }

    private void safeRunMapping(String mappingId) {
        try {
            syncEngine.runMapping(mappingId, SyncLog.SyncTrigger.SCHEDULED);
        } catch (Exception e) {
            log.error("映射定时同步异常: mapping={}", mappingId, e);
        }
    }

    private void safeRunDataSource(String dataSourceId) {
        try {
            syncEngine.runDataSource(dataSourceId, SyncLog.SyncTrigger.SCHEDULED);
        } catch (Exception e) {
            log.error("数据源定时同步异常: ds={}", dataSourceId, e);
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdown();
    }
}
