package com.example.fulfillment.config;

import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SDK 配置属性
 * <p>
 * 所有属性以 {@code fulfillment.*} 为前缀，可在 application.yml 中配置。
 * <p>
 * 由 {@link FulfillmentCoreAutoConfiguration} 通过 {@code @EnableConfigurationProperties} 自动注册。
 */
@ConfigurationProperties(prefix = "fulfillment")
public class FulfillmentProperties {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentProperties.class);

    /** 默认最大重试次数 */
    private int defaultMaxRetryCount = 3;

    /** 幂等记录过期时间（小时） */
    private int idempotentExpireHours = 24;

    /** 错误信息最大长度（防止数据库字段溢出） */
    private int maxErrorMessageLength = 500;

    /** 结果摘要最大长度 */
    private int maxResultSummaryLength = 500;

    /** Handler 执行超时时间（秒），0 表示不设置超时 */
    private int handlerTimeoutSeconds = 30;

    /** 超时控制线程池核心线程数 */
    private int handlerThreadPoolCoreSize = 2;

    /** 超时控制线程池最大线程数 */
    private int handlerThreadPoolMaxSize = 10;

    /** 超时控制线程池空闲线程存活时间（秒） */
    private int handlerThreadPoolKeepAliveSeconds = 60;

    /** 超时控制线程池队列容量 */
    private int handlerThreadPoolQueueCapacity = 200;

    // ==================== 自动重试配置 ====================

    /** 每次自动重试最多处理的任务数，防止一次加载过多 */
    private int retryBatchSize = 50;

    /** 重试退避基准时间（秒），实际间隔 = baseSeconds * 2^(retryCount-1) */
    private int retryBackoffBaseSeconds = 60;

    /** PROCESSING 状态超时时间（分钟），超过此时间的 PROCESSING 任务视为卡死 */
    private int processingTimeoutMinutes = 30;

    @PostConstruct
    public void validate() {
        if (defaultMaxRetryCount < 0) {
            throw new IllegalArgumentException("fulfillment.default-max-retry-count 不能为负数，当前值: " + defaultMaxRetryCount);
        }
        if (idempotentExpireHours <= 0) {
            throw new IllegalArgumentException("fulfillment.idempotent-expire-hours 必须大于 0，当前值: " + idempotentExpireHours);
        }
        if (maxErrorMessageLength <= 0) {
            throw new IllegalArgumentException("fulfillment.max-error-message-length 必须大于 0，当前值: " + maxErrorMessageLength);
        }
        if (handlerTimeoutSeconds < 0) {
            throw new IllegalArgumentException("fulfillment.handler-timeout-seconds 不能为负数，当前值: " + handlerTimeoutSeconds);
        }
        if (handlerThreadPoolCoreSize <= 0) {
            throw new IllegalArgumentException("fulfillment.handler-thread-pool-core-size 必须大于 0，当前值: " + handlerThreadPoolCoreSize);
        }
        if (handlerThreadPoolMaxSize < handlerThreadPoolCoreSize) {
            throw new IllegalArgumentException("fulfillment.handler-thread-pool-max-size 不能小于 core-size");
        }
        if (retryBatchSize <= 0) {
            throw new IllegalArgumentException("fulfillment.retry-batch-size 必须大于 0，当前值: " + retryBatchSize);
        }
        if (retryBackoffBaseSeconds < 0) {
            throw new IllegalArgumentException("fulfillment.retry-backoff-base-seconds 不能为负数，当前值: " + retryBackoffBaseSeconds);
        }
        if (processingTimeoutMinutes <= 0) {
            throw new IllegalArgumentException("fulfillment.processing-timeout-minutes 必须大于 0，当前值: " + processingTimeoutMinutes);
        }
        log.info("Fulfillment SDK 配置加载完成: maxRetry={}, timeout={}s, threadPool={}/{}, retryBatch={}, backoff={}s, procTimeout={}min",
                defaultMaxRetryCount, handlerTimeoutSeconds, handlerThreadPoolCoreSize, handlerThreadPoolMaxSize,
                retryBatchSize, retryBackoffBaseSeconds, processingTimeoutMinutes);
    }

    // ==================== Getters & Setters ====================

    public int getDefaultMaxRetryCount() { return defaultMaxRetryCount; }
    public void setDefaultMaxRetryCount(int defaultMaxRetryCount) { this.defaultMaxRetryCount = defaultMaxRetryCount; }
    public int getIdempotentExpireHours() { return idempotentExpireHours; }
    public void setIdempotentExpireHours(int idempotentExpireHours) { this.idempotentExpireHours = idempotentExpireHours; }
    public int getMaxErrorMessageLength() { return maxErrorMessageLength; }
    public void setMaxErrorMessageLength(int maxErrorMessageLength) { this.maxErrorMessageLength = maxErrorMessageLength; }
    public int getMaxResultSummaryLength() { return maxResultSummaryLength; }
    public void setMaxResultSummaryLength(int maxResultSummaryLength) { this.maxResultSummaryLength = maxResultSummaryLength; }
    public int getHandlerTimeoutSeconds() { return handlerTimeoutSeconds; }
    public void setHandlerTimeoutSeconds(int handlerTimeoutSeconds) { this.handlerTimeoutSeconds = handlerTimeoutSeconds; }
    public int getHandlerThreadPoolCoreSize() { return handlerThreadPoolCoreSize; }
    public void setHandlerThreadPoolCoreSize(int handlerThreadPoolCoreSize) { this.handlerThreadPoolCoreSize = handlerThreadPoolCoreSize; }
    public int getHandlerThreadPoolMaxSize() { return handlerThreadPoolMaxSize; }
    public void setHandlerThreadPoolMaxSize(int handlerThreadPoolMaxSize) { this.handlerThreadPoolMaxSize = handlerThreadPoolMaxSize; }
    public int getHandlerThreadPoolKeepAliveSeconds() { return handlerThreadPoolKeepAliveSeconds; }
    public void setHandlerThreadPoolKeepAliveSeconds(int v) { this.handlerThreadPoolKeepAliveSeconds = v; }
    public int getHandlerThreadPoolQueueCapacity() { return handlerThreadPoolQueueCapacity; }
    public void setHandlerThreadPoolQueueCapacity(int v) { this.handlerThreadPoolQueueCapacity = v; }
    public int getRetryBatchSize() { return retryBatchSize; }
    public void setRetryBatchSize(int retryBatchSize) { this.retryBatchSize = retryBatchSize; }
    public int getRetryBackoffBaseSeconds() { return retryBackoffBaseSeconds; }
    public void setRetryBackoffBaseSeconds(int retryBackoffBaseSeconds) { this.retryBackoffBaseSeconds = retryBackoffBaseSeconds; }
    public int getProcessingTimeoutMinutes() { return processingTimeoutMinutes; }
    public void setProcessingTimeoutMinutes(int processingTimeoutMinutes) { this.processingTimeoutMinutes = processingTimeoutMinutes; }
}
