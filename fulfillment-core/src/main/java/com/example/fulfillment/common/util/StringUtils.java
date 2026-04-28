package com.example.fulfillment.common.util;

import java.util.Locale;

/**
 * 字符串工具类
 */
public class StringUtils {
    
    /**
     * 安全截断字符串，防止数据库字段溢出
     */
    public static String truncate(String str, int maxLength) {
        if (str == null) {
            return null;
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength);
    }
    
    /**
     * 安全截断字符串，如果超长则添加省略号
     */
    public static String truncateWithEllipsis(String str, int maxLength) {
        if (str == null) {
            return null;
        }
        if (str.length() <= maxLength) {
            return str;
        }
        if (maxLength <= 3) {
            return truncate(str, maxLength);
        }
        return str.substring(0, maxLength - 3) + "...";
    }

    /**
     * 标准化幂等键
     * <p>
     * 对显式传入的幂等键执行 trim()，去除首尾空格。
     * 如果传入值为 null 或空白字符串，返回 null（表示"未提供"，由调用方决定是否自动生成）。
     * <p>
     * 示例：
     * <ul>
     *   <li>{@code " KEY_123 "} → {@code "KEY_123"}</li>
     *   <li>{@code "KEY_123"} → {@code "KEY_123"}（无变化）</li>
     *   <li>{@code null} → {@code null}</li>
     *   <li>{@code "  "} → {@code null}</li>
     *   <li>{@code ""} → {@code null}</li>
     * </ul>
     *
     * @param idempotentKey 原始幂等键（可能为 null）
     * @return 标准化后的幂等键；null 表示未提供有效值
     */
    public static String normalizeIdempotentKey(String idempotentKey) {
        if (idempotentKey == null) {
            return null;
        }
        String trimmed = idempotentKey.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 归一化生命周期动作编码
     * <p>
     * 执行以下标准化处理：
     * <ol>
     *   <li>去除首尾空格（trim）</li>
     *   <li>转换为大写（upper-case）</li>
     *   <li>空值校验（null 或空字符串会抛出异常）</li>
     * </ol>
     * <p>
     * 示例：
     * <ul>
     *   <li>{@code "revoke"} → {@code "REVOKE"}</li>
     *   <li>{@code " REVOKE "} → {@code "REVOKE"}</li>
     *   <li>{@code "GRANT"} → {@code "GRANT"}</li>
     *   <li>{@code null} → 抛出 {@link IllegalArgumentException}</li>
     *   <li>{@code "  "} → 抛出 {@link IllegalArgumentException}</li>
     * </ul>
     *
     * @param actionCode 原始动作编码
     * @return 归一化后的动作编码（大写、无空格）
     * @throws IllegalArgumentException 如果 actionCode 为 null 或空白字符串
     */
    public static String normalizeActionCode(String actionCode) {
        if (actionCode == null) {
            throw new IllegalArgumentException("actionCode cannot be null");
        }
        String trimmed = actionCode.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("actionCode cannot be blank");
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }
}
