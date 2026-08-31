package com.tongkey.domain;

/** 数据来源类型（规格文档 4.1）。 */
public enum SourceType {
    /** 本系统创建维护 */
    NATIVE,
    /** 第三方 SQL 数据源同步而来 */
    SYNCED,
    /** 通过开放 REST API 写入 */
    API
}
