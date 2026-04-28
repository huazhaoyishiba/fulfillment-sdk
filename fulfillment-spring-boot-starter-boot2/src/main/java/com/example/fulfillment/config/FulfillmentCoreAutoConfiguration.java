package com.example.fulfillment.config;

import com.example.fulfillment.service.impl.FulfillmentHandlerRegistry;
import com.example.fulfillment.spi.FulfillmentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.stream.Collectors;

/**
 * SDK 核心基础能力自动装配
 * <p>
 * 职责：只装配"纯内核/运行时基础能力"，不依赖任何持久化实现。
 * <p>
 * 装配内容：
 * - FulfillmentProperties（配置属性）
 * - FulfillmentHandlerRegistry（Handler 注册表）
 * <p>
 * 不装配内容：
 * - Repository 实现（由 storage-mybatis 或用户自定义提供）
 * - 完整发放服务（由 FulfillmentRuntimeAutoConfiguration 条件装配）
 * <p>
 * 设计原则：
 * - 不假设 DataSource 存在
 * - 不假设 JDBC/MyBatis 存在
 * - 只提供基础内核能力，让项目能正常启动
 */
@Configuration
@EnableConfigurationProperties(FulfillmentProperties.class)
public class FulfillmentCoreAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentCoreAutoConfiguration.class);

    // FulfillmentProperties 由 @EnableConfigurationProperties 自动注册，无需显式 @Bean。
    // Spring Boot 会自动将 application.yml 中 fulfillment.* 前缀的配置绑定到该 Bean。

    /**
     * Handler 注册表 Bean
     * <p>
     * 这是纯内核能力，不依赖持久化。
     * 使用 {@link ObjectProvider} 延迟获取 Handler 列表，适合 starter 风格：
     * - 容器中无 Handler 时不会报错，得到空列表
     * - 支持有序流处理（{@code orderedStream()}）
     * - 对可选依赖更友好
     */
    @Bean
    @ConditionalOnMissingBean(FulfillmentHandlerRegistry.class)
    public FulfillmentHandlerRegistry fulfillmentHandlerRegistry(ObjectProvider<FulfillmentHandler> handlersProvider) {
        List<FulfillmentHandler> handlers = handlersProvider.orderedStream().collect(Collectors.toList());
        log.info("自动装配 FulfillmentHandlerRegistry（核心基础能力），Handler 数量: {}", handlers.size());
        if (handlers.isEmpty()) {
            log.warn("未发现任何 FulfillmentHandler 实现，请注册至少一个 Handler Bean 以使用发放功能");
        }
        return new FulfillmentHandlerRegistry(handlers);
    }
}
