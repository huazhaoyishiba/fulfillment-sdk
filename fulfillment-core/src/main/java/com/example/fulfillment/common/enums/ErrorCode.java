package com.example.fulfillment.common.enums;

/**
 * 错误码枚举
 * 用于区分不同类型的错误，决定是否可重试
 */
public enum ErrorCode {
    
    // ========== 可重试的错误（临时性错误） ==========
    
    /**
     * Handler 执行超时
     * 可重试：可能是网络抖动或系统负载高
     */
    HANDLER_TIMEOUT("HANDLER_TIMEOUT", true, "Handler execution timeout"),
    
    /**
     * Handler 执行异常（系统异常）
     * 可重试：可能是临时性系统错误
     */
    HANDLER_EXCEPTION("HANDLER_EXCEPTION", true, "Handler execution exception"),
    
    /**
     * 网络错误
     * 可重试：网络临时不可用
     */
    NETWORK_ERROR("NETWORK_ERROR", true, "Network error"),
    
    /**
     * 数据库连接错误
     * 可重试：数据库临时不可用
     */
    DATABASE_ERROR("DATABASE_ERROR", true, "Database connection error"),
    
    /**
     * 下游服务不可用
     * 可重试：下游服务临时不可用
     */
    DOWNSTREAM_UNAVAILABLE("DOWNSTREAM_UNAVAILABLE", true, "Downstream service unavailable"),
    
    // ========== 不可重试的错误（永久性错误） ==========
    
    /**
     * 无效的权益类型
     * 不可重试：配置错误，重试也不会成功
     */
    INVALID_BENEFIT_TYPE("INVALID_BENEFIT_TYPE", false, "Invalid benefit type"),
    
    /**
     * 未找到对应的 Handler
     * 不可重试：缺少实现，重试也不会成功
     */
    HANDLER_NOT_FOUND("HANDLER_NOT_FOUND", false, "Handler not found"),
    
    /**
     * Handler 返回业务失败
     * 不可重试：业务逻辑拒绝，重试也不会成功（除非业务规则改变）
     */
    HANDLER_BUSINESS_FAILURE("HANDLER_BUSINESS_FAILURE", false, "Handler business failure"),
    
    /**
     * 参数校验失败
     * 不可重试：参数错误，重试也不会成功
     */
    VALIDATION_ERROR("VALIDATION_ERROR", false, "Parameter validation error"),
    
    /**
     * 数据不一致
     * 不可重试：数据问题，需要人工介入
     */
    DATA_INCONSISTENCY("DATA_INCONSISTENCY", false, "Data inconsistency"),
    
    /**
     * 通用错误（未知类型）
     * 默认不可重试，需要根据实际情况判断
     */
    FULFILLMENT_ERROR("FULFILLMENT_ERROR", false, "Fulfillment error");
    
    private final String code;
    private final boolean retryable;
    private final String description;
    
    ErrorCode(String code, boolean retryable, String description) {
        this.code = code;
        this.retryable = retryable;
        this.description = description;
    }
    
    public String getCode() {
        return code;
    }
    
    /**
     * 是否可重试
     */
    public boolean isRetryable() {
        return retryable;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 根据错误码字符串获取枚举
     */
    public static ErrorCode fromCode(String code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.code.equals(code)) {
                return errorCode;
            }
        }
        return FULFILLMENT_ERROR; // 默认返回通用错误
    }
}
