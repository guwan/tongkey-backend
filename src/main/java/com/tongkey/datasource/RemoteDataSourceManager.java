package com.tongkey.datasource;

import com.tongkey.common.ApiException;
import com.tongkey.common.CryptoUtil;
import com.tongkey.common.ErrorCode;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 第三方数据源动态连接池管理（规格文档 3.2）：
 * 按配置动态创建/复用 {@link DataSource}，通过 {@link JdbcTemplate} 执行配置化 SQL。
 * <p>配置变更（连接信息、启用状态）时重建对应连接池。</p>
 */
@Component
public class RemoteDataSourceManager {

    private static final Logger log = LoggerFactory.getLogger(RemoteDataSourceManager.class);

    private final CryptoUtil crypto;
    private final Map<String, HikariDataSource> pools = new ConcurrentHashMap<>();
    private final Map<String, String> poolSignatures = new ConcurrentHashMap<>();

    public RemoteDataSourceManager(CryptoUtil crypto) {
        this.crypto = crypto;
    }

    /** 获取（必要时重建）数据源对应的 JdbcTemplate。 */
    public JdbcTemplate template(DataSourceConfig cfg) {
        return new JdbcTemplate(pool(cfg));
    }

    public synchronized DataSource pool(DataSourceConfig cfg) {
        String signature = cfg.getJdbcUrl() + "|" + cfg.getUsername() + "|" + cfg.getPassword() + "|" + cfg.getConnectTimeoutSeconds();
        HikariDataSource existing = pools.get(cfg.getId());
        if (existing != null && signature.equals(poolSignatures.get(cfg.getId()))) {
            return existing;
        }
        closeQuietly(existing);
        HikariDataSource ds = create(cfg);
        pools.put(cfg.getId(), ds);
        poolSignatures.put(cfg.getId(), signature);
        return ds;
    }

    private HikariDataSource create(DataSourceConfig cfg) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(cfg.getJdbcUrl());
        hc.setUsername(cfg.getUsername());
        hc.setPassword(crypto.decrypt(cfg.getPassword()));
        hc.setMaximumPoolSize(3);
        hc.setMinimumIdle(0);
        hc.setConnectionTimeout(cfg.getConnectTimeoutSeconds() * 1000L);
        hc.setIdleTimeout(120_000);
        hc.setMaxLifetime(600_000);
        hc.setPoolName("tk-remote-" + cfg.getName());
        hc.setReadOnly(true); // 拉取场景只读
        try {
            return new HikariDataSource(hc);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.CONNECTION_FAILED, "创建数据源连接池失败: " + e.getMessage(), e);
        }
    }

    /** 配置删除/禁用时关闭连接池。 */
    public synchronized void evict(String configId) {
        closeQuietly(pools.remove(configId));
        poolSignatures.remove(configId);
    }

    public synchronized void closeAll() {
        pools.values().forEach(this::closeQuietly);
        pools.clear();
        poolSignatures.clear();
    }

    private void closeQuietly(HikariDataSource ds) {
        if (ds != null) {
            try {
                ds.close();
            } catch (Exception e) {
                log.warn("关闭连接池失败: {}", e.getMessage());
            }
        }
    }
}
