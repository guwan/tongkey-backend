package com.tongkey.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一响应结构：{ code, message, data, traceId }（规格文档 7.2）。
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(int code, String message, T data, String traceId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "成功", data, TraceContext.traceId());
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> error(ErrorCode ec, String message) {
        return new ApiResponse<>(ec.getCode(), message != null ? message : ec.getMessage(), null, TraceContext.traceId());
    }

    public static <T> ApiResponse<T> error(ErrorCode ec) {
        return error(ec, null);
    }
}
