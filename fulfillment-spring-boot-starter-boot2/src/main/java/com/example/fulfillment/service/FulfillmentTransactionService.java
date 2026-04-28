package com.example.fulfillment.service;

import com.example.fulfillment.domain.entity.FulfillmentLog;
import com.example.fulfillment.domain.entity.FulfillmentTask;
import com.example.fulfillment.dto.ConfirmPaymentRequest;
import com.example.fulfillment.dto.ExecuteLifecycleActionRequest;
import com.example.fulfillment.dto.FulfillmentResponse;
import com.example.fulfillment.dto.ManualFulfillRequest;

import java.time.LocalDateTime;

/**
 * 事务服务接口
 * <p>
 * 用于解决事务自调用问题：将带事务的方法拆分到独立 Bean，确保事务生效。
 * <p>
 * 设计原则：
 * - 所有带 @Transactional 的方法都在此接口中
 * - 通过独立的 Bean 调用，避免 Spring AOP 代理失效
 */
public interface FulfillmentTransactionService {

    /**
     * 创建支付确认任务（事务内）
     * 
     * @return 创建的任务，如果幂等命中则返回 null
     */
    FulfillmentTask createTaskTransactional(ConfirmPaymentRequest request);

    /**
     * 创建手动补发任务（事务内）
     * 
     * @return 创建的任务，如果幂等命中则返回 null
     */
    FulfillmentTask createManualTaskTransactional(ManualFulfillRequest request);

    /**
     * 创建生命周期动作任务（事务内）
     * <p>
     * 通用入口，支持 GRANT / REVOKE / RENEW 等任意动作。
     * 
     * @param request 生命周期动作请求
     * @return 创建的任务，如果幂等命中则返回 null
     */
    FulfillmentTask createLifecycleTaskTransactional(ExecuteLifecycleActionRequest request);

    /**
     * 保存成功结果（事务内）
     */
    FulfillmentResponse saveSuccessResult(FulfillmentTask task, FulfillmentLog logRecord, String summary);

    /**
     * 保存失败结果（事务内）
     */
    FulfillmentResponse saveFailureResult(FulfillmentTask task, FulfillmentLog logRecord,
                                          LocalDateTime start, String errorCode,
                                          String errorMessage, boolean retryable);
}
