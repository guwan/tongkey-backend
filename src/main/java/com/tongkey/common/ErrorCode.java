package com.tongkey.common;

/**
 * 统一错误码体系（规格文档 7.2）。
 * <p>编码规则：前两位为大类（10 通用 / 20 鉴权 / 30 资源 / 40 业务 / 50 系统）。</p>
 */
public enum ErrorCode {

    OK(0, "成功"),
    INVALID_PARAM(10001, "参数错误"),
    NOT_FOUND(30001, "资源不存在"),
    CONFLICT(40001, "数据冲突"),
    DUPLICATE_KEY(40002, "唯一标识已存在"),
    SQL_NOT_READONLY(40101, "SQL 未通过只读校验，仅允许 SELECT / WITH 开头的查询语句"),
    CONNECTION_FAILED(40102, "数据库连接失败"),
    SYNC_FAILED(40103, "同步执行失败"),
    PUSH_FAILED(40104, "推送执行失败"),
    UNAUTHORIZED(20001, "未认证或凭证无效"),
    FORBIDDEN(20002, "无权限访问该资源"),
    RATE_LIMITED(20003, "请求过于频繁，已触发限流"),
    SIGNATURE_INVALID(20004, "签名校验失败"),
    TIMESTAMP_EXPIRED(20005, "请求时间戳超出允许范围"),
    INTERNAL(50000, "系统内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
