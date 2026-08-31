package com.tongkey.common;

/**
 * 操作者上下文：记录"谁、通过什么渠道"做了变更（规格文档 4.1 / 10 审计要求）。
 * <p>渠道：CONSOLE（管理台）、API:{clientId}（开放接口）、SYNC:{数据源名}（同步）。</p>
 */
public final class OperatorContext {

    public static final String CHANNEL_CONSOLE = "CONSOLE";
    public static final String CHANNEL_SYSTEM = "SYSTEM";

    private static final ThreadLocal<String> OPERATOR = new ThreadLocal<>();
    private static final ThreadLocal<String> CHANNEL = new ThreadLocal<>();

    private OperatorContext() {
    }

    public static void set(String operator, String channel) {
        OPERATOR.set(operator);
        CHANNEL.set(channel);
    }

    public static void clear() {
        OPERATOR.remove();
        CHANNEL.remove();
    }

    public static String operator() {
        String v = OPERATOR.get();
        return v != null ? v : "anonymous";
    }

    public static String channel() {
        String v = CHANNEL.get();
        return v != null ? v : CHANNEL_CONSOLE;
    }

    /** 合并后的操作者标识，写入 created_by/updated_by，如 "API:his-system"、"SYNC:HIS主库"。 */
    public static String composedOperator() {
        String ch = channel();
        String op = operator();
        if (CHANNEL_CONSOLE.equals(ch) || CHANNEL_SYSTEM.equals(ch)) {
            return op;
        }
        if (op == null || op.isBlank() || "anonymous".equals(op)) {
            return ch;
        }
        return ch + ":" + op;
    }
}
