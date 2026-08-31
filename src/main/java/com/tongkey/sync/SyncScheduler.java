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
 * 定时拉取调度（规格文档 3.2 / 5.3）：按数据源的 schedule_cron 动态注册/刷新任务。
 * <p>配置新增、修改、删除后调用 {@link #refresh()} 重新注册。</p>
 */
@Component
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

    private final DataSourceRepository dataSourceRepository;
    private final SyncEngine syncEngine;
    private final ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    private final Map<String, ScheduledFuture<?>> tasks = new HashMap<>();

    public SyncScheduler(DataSourceRepository dataSourceRepository, SyncEngine syncEngine) {
        this.dataSourceRepository = dataSourceRepository;
        this.syncEngine = syncEngine;
    }

    @PostConstruct
    void init() {
        scheduler.setPoolSize(2);
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
        for (DataSourceConfig ds : dataSourceRepository.findByEnabledTrue()) {
            String cron = ds.getScheduleCron();
            if (cron == null || cron.isBlank()) {
                continue;
            }
            try {
                ScheduledFuture<?> future = scheduler.schedule(
                        () -> safeRun(ds.getId()),
                        new CronTrigger(cron));
                tasks.put(ds.getId(), future);
                log.info("已注册定时同步: {} [{}] cron={}", ds.getName(), ds.getId(), cron);
            } catch (IllegalArgumentException e) {
                log.error("数据源 {} 的 cron 表达式无效: {}", ds.getName(), cron);
            }
        }
    }

    private void safeRun(String dataSourceId) {
        try {
            syncEngine.runDataSource(dataSourceId, SyncLog.SyncTrigger.SCHEDULED);
        } catch (Exception e) {
            log.error("定时同步异常: ds={}", dataSourceId, e);
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdown();
    }
}
