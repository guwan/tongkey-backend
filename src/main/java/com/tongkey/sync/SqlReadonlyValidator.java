package com.tongkey.sync;

import com.tongkey.common.ApiException;
import com.tongkey.common.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * SQL 只读校验（规格文档 5.4）：
 * <ul>
 *   <li>仅允许 SELECT 或 WITH（CTE）开头的查询，Oracle/SQL Server 的 CTE 只读查询同样放行；</li>
 *   <li>禁止多语句（分号分隔），避免误配置破坏源库。</li>
 * </ul>
 */
@Component
public class SqlReadonlyValidator {

    private static final Pattern READONLY_START = Pattern.compile("^\\s*(select|with)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DANGEROUS = Pattern.compile(
            "\\b(insert|update|delete|drop|truncate|alter|create|merge|grant|revoke|exec|execute|call)\\b",
            Pattern.CASE_INSENSITIVE);

    /** 校验并返回规范化（去除首尾空白与末尾分号）的 SQL。 */
    public String validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_PARAM, "SQL 不能为空");
        }
        String normalized = sql.trim();
        if (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        if (normalized.contains(";")) {
            throw new ApiException(ErrorCode.SQL_NOT_READONLY, "不允许包含多条语句（检测到分号分隔）");
        }
        if (!READONLY_START.matcher(normalized).find()) {
            throw new ApiException(ErrorCode.SQL_NOT_READONLY);
        }
        // 简单黑名单拦截常见写关键字（不追求完备，最终以源库只读账号权限为准）
        String noStrings = normalized.replaceAll("'[^']*'", "''");
        if (DANGEROUS.matcher(noStrings).find()) {
            throw new ApiException(ErrorCode.SQL_NOT_READONLY, "SQL 中检测到写操作关键字");
        }
        return normalized;
    }
}
