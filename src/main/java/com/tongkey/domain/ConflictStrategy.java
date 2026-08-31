package com.tongkey.domain;

/** 同步冲突处理策略（规格文档 4.1 关键设计原则）。 */
public enum ConflictStrategy {
    /** 同步数据覆盖本地 */
    SYNC_OVERRIDE,
    /** 本地被手工修改过则保留本地数据 */
    SYNC_SKIP_IF_MODIFIED,
    /** 仅填充本地为空的字段（字段级合并） */
    MERGE_FIELD_LEVEL
}
