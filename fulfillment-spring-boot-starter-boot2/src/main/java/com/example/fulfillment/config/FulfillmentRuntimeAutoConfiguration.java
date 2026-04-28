package com.example.fulfillment.config;

import com.example.fulfillment.core.repository.FulfillmentLogRepository;
import com.example.fulfillment.core.repository.FulfillmentTaskRepository;
import com.example.fulfillment.core.repository.IdempotentRecordRepository;
import com.example.fulfillment.core.repository.PaymentRecordRepository;
import com.example.fulfillment.service.FulfillmentApplicationService;
import com.example.fulfillment.service.FulfillmentTransactionService;
import com.example.fulfillment.service.impl.FulfillmentApplicationServiceImpl;
import com.example.fulfillment.service.impl.FulfillmentHandlerRegistry;
import com.example.fulfillment.service.impl.FulfillmentTransactionServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SDK 完整运行时服务自动装配
 * <p>
 * 职责：装配完整的发放服务能力，但需要满足前提条件。
 * <p>
 * 前提条件（必须同时满足）：
 * - PaymentRecordRepository Bean 存在
 * - IdempotentRecordRepository Bean 存在
 * - FulfillmentTaskRepository Bean 存在
 * - FulfillmentLogRepository Bean 存在
 * <p>
 * 装配内容：
 * - FulfillmentTransactionService（事务服务）
 * - FulfillmentApplicationService（完整发放服务）
 * <p>
 * 设计原则：
 * - 只有持久化实现存在时，才装配完整服务
 * - 如果没有持久化实现，不装配这些 Bean，但项目仍能正常启动
 * - 这样实现了"内核默认中立，持久化作为可选实现接入"
 * <p>
 * 注意：必须使用 @AutoConfigureAfter 确保在 Repository Bean 注册之后再评估条件，
 * 否则 @ConditionalOnBean 可能因为顺序问题而误判为 false。
 * 使用 name（String）引用避免对 storage-mybatis 模块的编译时依赖。
 */
@Configuration
@AutoConfigureAfter(name = {
        "com.example.fulfillment.config.MyBatisAutoConfiguration",
        "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration"
})
@ConditionalOnBean({
        PaymentRecordRepository.class,
        IdempotentRecordRepository.class,
        FulfillmentTaskRepository.class,
        FulfillmentLogRepository.class
})
public class FulfillmentRuntimeAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentRuntimeAutoConfiguration.class);

    /**
     * 事务服务 Bean（用于解决事务自调用问题）
     * <p>
     * 条件：4 个 Repository Bean 都存在时才装配。
     */
    @Bean
    @ConditionalOnMissingBean(FulfillmentTransactionService.class)
    public FulfillmentTransactionService fulfillmentTransactionService(
            PaymentRecordRepository paymentRecordRepository,
            IdempotentRecordRepository idempotentRecordRepository,
            FulfillmentTaskRepository fulfillmentTaskRepository,
            FulfillmentLogRepository fulfillmentLogRepository,
            FulfillmentProperties properties) {
        log.info("自动装配 FulfillmentTransactionService（完整运行时服务）");
        return new FulfillmentTransactionServiceImpl(
                paymentRecordRepository,
                idempotentRecordRepository,
                fulfillmentTaskRepository,
                fulfillmentLogRepository,
                properties);
    }

    /**
     * 核心服务实现 Bean
     * <p>
     * 条件：4 个 Repository Bean 都存在时才装配。
     */
    @Bean
    @ConditionalOnMissingBean(FulfillmentApplicationService.class)
    @ConditionalOnBean(FulfillmentTransactionService.class)
    public FulfillmentApplicationService fulfillmentApplicationService(
            FulfillmentTransactionService transactionService,
            IdempotentRecordRepository idempotentRecordRepository,
            FulfillmentTaskRepository fulfillmentTaskRepository,
            FulfillmentLogRepository fulfillmentLogRepository,
            FulfillmentHandlerRegistry handlerRegistry,
            FulfillmentProperties properties) {
        log.info("自动装配 FulfillmentApplicationService（完整运行时服务）");
        return new FulfillmentApplicationServiceImpl(
                transactionService,
                idempotentRecordRepository,
                fulfillmentTaskRepository,
                fulfillmentLogRepository,
                handlerRegistry,
                properties);
    }

}
