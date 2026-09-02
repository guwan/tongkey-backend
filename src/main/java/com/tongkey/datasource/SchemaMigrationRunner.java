package com.tongkey.datasource;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 开发环境临时 Schema 迁移：为历史数据源遗留的 tk_ 表补新列。
 * Hibernate ddl-auto: update 对旧表结构有时不能正确识别新增列，
 * 这里用 JdbcTemplate 直接 ALTER TABLE，列已存在时忽略错误。
 * 仅 dev 环境生效；生产应使用 Flyway/Liquibase 管理迁移脚本。
 */
@Component
@Profile("dev")
public class SchemaMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public SchemaMigrationRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        safeAlter("ALTER TABLE tk_sync_mapping ADD COLUMN sync_mode VARCHAR(16) NOT NULL DEFAULT 'FULL'");
        safeAlter("ALTER TABLE tk_sync_mapping ADD COLUMN incremental_column VARCHAR(128)");
        safeAlter("ALTER TABLE tk_sync_mapping ADD COLUMN schedule_cron VARCHAR(64)");
    }

    private void safeAlter(String sql) {
        try {
            jdbc.execute(sql);
            System.out.println("[SchemaMigrate] OK: " + sql);
        } catch (Exception e) {
            // 列已存在时 Postgres 会报错，忽略即可
            System.out.println("[SchemaMigrate] SKIP (可能已存在): " + e.getMessage());
        }
    }
}
