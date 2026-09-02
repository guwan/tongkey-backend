package com.tongkey.datasource;

import com.tongkey.common.ApiException;
import com.tongkey.common.CryptoUtil;
import com.tongkey.common.ErrorCode;
import com.tongkey.datasource.dialect.DbDialectFactory;
import com.tongkey.sync.SyncScheduler;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 数据源配置管理：密码加密存储、连接测试、调度注册联动。
 */
@Service
public class DataSourceService {

    private final DataSourceRepository repository;
    private final CryptoUtil crypto;
    private final DbDialectFactory dialectFactory;
    private final RemoteDataSourceManager poolManager;
    private final SyncScheduler scheduler;

    public DataSourceService(DataSourceRepository repository, CryptoUtil crypto, DbDialectFactory dialectFactory,
                             RemoteDataSourceManager poolManager, SyncScheduler scheduler) {
        this.repository = repository;
        this.crypto = crypto;
        this.dialectFactory = dialectFactory;
        this.poolManager = poolManager;
        this.scheduler = scheduler;
    }

    public List<DataSourceConfig> listAll() {
        return repository.findAll();
    }

    public DataSourceConfig require(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "数据源不存在: " + id));
    }

    @Transactional
    public DataSourceConfig create(DataSourceConfig cfg) {
        cfg.setPassword(crypto.encrypt(cfg.getPassword()));
        cfg = repository.save(cfg);
        scheduler.refresh();
        return cfg;
    }

    @Transactional
    public DataSourceConfig update(String id, DataSourceConfig req) {
        DataSourceConfig cfg = require(id);
        cfg.setName(req.getName());
        cfg.setDbType(req.getDbType());
        cfg.setJdbcUrl(req.getJdbcUrl());
        cfg.setUsername(req.getUsername());
        // 密码为空串/脱敏占位表示保持不变
        if (req.getPassword() != null && !req.getPassword().isBlank() && !req.getPassword().contains("****")) {
            cfg.setPassword(crypto.encrypt(req.getPassword()));
        }
        cfg.setEnabled(req.isEnabled());
        cfg.setScheduleCron(req.getScheduleCron());
        cfg.setConnectTimeoutSeconds(req.getConnectTimeoutSeconds() > 0 ? req.getConnectTimeoutSeconds() : 10);
        cfg.setNotes(req.getNotes());
        cfg = repository.save(cfg);
        poolManager.evict(id);
        scheduler.refresh();
        return cfg;
    }

    @Transactional
    public void delete(String id) {
        require(id);
        repository.deleteById(id);
        poolManager.evict(id);
        scheduler.refresh();
    }

    /**
     * 连接测试：针对每种 db_type 执行最小化探测查询（规格文档 5.1.1）。
     *
     * @return 探测耗时毫秒
     */
    public long testConnection(String id) {
        DataSourceConfig cfg = require(id);
        JdbcTemplate template = poolManager.template(cfg);
        template.setQueryTimeout(Math.max(5, cfg.getConnectTimeoutSeconds()));
        String probe = dialectFactory.require(cfg.getDbType()).probeQuery();
        long start = System.currentTimeMillis();
        try {
            template.queryForObject(probe, Object.class);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.CONNECTION_FAILED, "连接测试失败: " + e.getMessage(), e);
        }
        return System.currentTimeMillis() - start;
    }
}
