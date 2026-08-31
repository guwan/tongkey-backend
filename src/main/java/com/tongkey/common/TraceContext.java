package com.tongkey.common;

import org.slf4j.MDC;

/**
 * TraceId 上下文，贯穿请求链路，便于日志串联。
 */
public final class TraceContext {

    public static final String MDC_KEY = "traceId";

    private TraceContext() {
    }

    public static String traceId() {
        String v = MDC.get(MDC_KEY);
        return v != null ? v : "-";
    }
}
