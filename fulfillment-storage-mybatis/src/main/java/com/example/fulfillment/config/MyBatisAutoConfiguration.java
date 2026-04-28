package com.example.fulfillment.config;

import com.example.fulfillment.core.repository.FulfillmentLogRepository;
import com.example.fulfillment.core.repository.FulfillmentTaskRepository;
import com.example.fulfillment.core.repository.IdempotentRecordRepository;
import com.example.fulfillment.core.repository.PaymentRecordRepository;
import com.example.fulfillment.domain.mapper.FulfillmentLogMapper;
import com.example.fulfillment.domain.mapper.FulfillmentTaskMapper;
import com.example.fulfillment.domain.mapper.IdempotentRecordMapper;
import com.example.fulfillment.domain.mapper.PaymentRecordMapper;
import com.example.fulfillment.extension.repository.mybatis.MyBatisFulfillmentLogRepository;
import com.example.fulfillment.extension.repository.mybatis.MyBatisFulfillmentTaskRepository;
import com.example.fulfillment.extension.repository.mybatis.MyBatisIdempotentRecordRepository;
import com.example.fulfillment.extension.repository.mybatis.MyBatisPaymentRecordRepository;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 持久化实现自动装配配置
 * <p>
 * 此配置类位于 fulfillment-storage-mybatis 模块中，负责：
 * 1. 扫描 MyBatis Mapper 接口
 * 2. 自动注册 MyBatis 实现的 Repository Bean
 * <p>
 * 生效条件（同时满足）：
 * - MyBatis 类存在于 classpath（@ConditionalOnClass）
 * - SqlSessionFactory Bean 已注册（@ConditionalOnBean）—— 确保数据源和 MyBatis 真正可用
 * <p>
 * 加载顺序：
 * - 必须在 MyBatis 官方 MybatisAutoConfiguration 之后加载（@AutoConfigureAfter）
 * - 因为 @ConditionalOnBean(SqlSessionFactory.class) 需要 SqlSessionFactory 已经创建
 * <p>
 * 设计原则：
 * - 只有 MyBatis 类存在 + SqlSessionFactory 真正可用时才加载
 * - 如果用户自己注册了 Repository Bean，则优先使用用户的（@ConditionalOnMissingBean）
 * - 模块职责清晰：MyBatis 相关配置完全在 MyBatis 模块中
 */
@Configuration
@ConditionalOnClass(SqlSessionFactory.class)
@ConditionalOnBean(SqlSessionFactory.class)
@AutoConfigureAfter(name = "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration")
@MapperScan("com.example.fulfillment.domain.mapper")
public class MyBatisAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MyBatisAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(PaymentRecordRepository.class)
    public PaymentRecordRepository paymentRecordRepository(PaymentRecordMapper mapper) {
        log.info("自动装配 PaymentRecordRepository -> MyBatis 实现");
        return new MyBatisPaymentRecordRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(IdempotentRecordRepository.class)
    public IdempotentRecordRepository idempotentRecordRepository(IdempotentRecordMapper mapper) {
        log.info("自动装配 IdempotentRecordRepository -> MyBatis 实现");
        return new MyBatisIdempotentRecordRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(FulfillmentTaskRepository.class)
    public FulfillmentTaskRepository fulfillmentTaskRepository(FulfillmentTaskMapper mapper) {
        log.info("自动装配 FulfillmentTaskRepository -> MyBatis 实现");
        return new MyBatisFulfillmentTaskRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean(FulfillmentLogRepository.class)
    public FulfillmentLogRepository fulfillmentLogRepository(FulfillmentLogMapper mapper) {
        log.info("自动装配 FulfillmentLogRepository -> MyBatis 实现");
        return new MyBatisFulfillmentLogRepository(mapper);
    }
}
