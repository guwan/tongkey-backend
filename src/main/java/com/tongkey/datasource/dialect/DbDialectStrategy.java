package com.tongkey.datasource.dialect;

import com.tongkey.datasource.DbType;

/**
 * 数据库方言策略（规格文档 5.1.1 实现建议）：
 * 封装各库差异点（探测查询、预览限行语法等），避免核心同步逻辑散落 if/else。
 */
public interface DbDialectStrategy {

    DbType type();

    /** 连接测试的最小化探测查询 */
    String probeQuery();

    /** 将只读查询包装为限行预览（用于管理台 SQL 调试预览） */
    String wrapLimit(String sql, int limit);

    /** 增量条件的绑定参数占位符风格（统一使用 :lastSyncValue 命名参数，由执行层转为 ? 绑定） */
    default String incrementalCondition(String column) {
        return " AND " + column + " > ? ";
    }
}
