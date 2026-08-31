package com.tongkey.push;

/** 推送触发时机（规格文档 6.1）。 */
public enum TriggerEvent {
    /** 系统初始化 / 全量推送一次 */
    ON_INIT,
    ON_CREATE,
    ON_UPDATE,
    ON_DELETE
}
