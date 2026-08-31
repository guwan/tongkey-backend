package com.tongkey.datasource;

/** 数据源同步模式（规格文档 5.1）。 */
public enum SyncMode {
    /** 全量覆盖 */
    FULL,
    /** 增量：依据时间戳/自增 ID 字段 */
    INCREMENTAL
}
