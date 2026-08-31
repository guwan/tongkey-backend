package com.tongkey.datasource.dialect;

import com.tongkey.common.ApiException;
import com.tongkey.common.ErrorCode;
import com.tongkey.datasource.DbType;
import org.springframework.stereotype.Component;

/**
 * 方言策略工厂。五种数据库的差异集中在此，核心同步/预览逻辑不感知具体库类型。
 */
@Component
public class DbDialectFactory {

    public DbDialectStrategy of(DbType type) {
        return switch (type) {
            case MYSQL -> new DbDialectStrategy() {
                @Override
                public DbType type() {
                    return DbType.MYSQL;
                }

                @Override
                public String probeQuery() {
                    return "SELECT 1";
                }

                @Override
                public String wrapLimit(String sql, int limit) {
                    return "SELECT * FROM (" + sql + ") t_preview LIMIT " + limit;
                }
            };
            case MARIADB -> new DbDialectStrategy() {
                @Override
                public DbType type() {
                    return DbType.MARIADB;
                }

                @Override
                public String probeQuery() {
                    return "SELECT 1";
                }

                @Override
                public String wrapLimit(String sql, int limit) {
                    return "SELECT * FROM (" + sql + ") t_preview LIMIT " + limit;
                }
            };
            case POSTGRESQL -> new DbDialectStrategy() {
                @Override
                public DbType type() {
                    return DbType.POSTGRESQL;
                }

                @Override
                public String probeQuery() {
                    return "SELECT 1";
                }

                @Override
                public String wrapLimit(String sql, int limit) {
                    return "SELECT * FROM (" + sql + ") t_preview LIMIT " + limit;
                }
            };
            case ORACLE -> new DbDialectStrategy() {
                @Override
                public DbType type() {
                    return DbType.ORACLE;
                }

                @Override
                public String probeQuery() {
                    return "SELECT 1 FROM DUAL";
                }

                @Override
                public String wrapLimit(String sql, int limit) {
                    // Oracle 兼容 ROWNUM 写法；源 SQL 中允许 table@dblink 跨库引用，此处不做改写
                    return "SELECT * FROM (" + sql + ") WHERE ROWNUM <= " + limit;
                }
            };
            case SQLSERVER -> new DbDialectStrategy() {
                @Override
                public DbType type() {
                    return DbType.SQLSERVER;
                }

                @Override
                public String probeQuery() {
                    return "SELECT 1";
                }

                @Override
                public String wrapLimit(String sql, int limit) {
                    return "SELECT TOP " + limit + " * FROM (" + sql + ") t_preview";
                }
            };
        };
    }

    public DbDialectStrategy require(DbType type) {
        if (type == null) {
            throw new ApiException(ErrorCode.INVALID_PARAM, "db_type 不能为空");
        }
        return of(type);
    }
}
