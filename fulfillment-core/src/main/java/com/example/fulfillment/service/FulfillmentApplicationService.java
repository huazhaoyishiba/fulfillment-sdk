package com.example.fulfillment.service;

import com.example.fulfillment.dto.ConfirmPaymentRequest;
import com.example.fulfillment.dto.EntitlementStatusResponse;
import com.example.fulfillment.dto.ExecuteLifecycleActionRequest;
import com.example.fulfillment.dto.FulfillmentDetailResponse;
import com.example.fulfillment.dto.FulfillmentResponse;
import com.example.fulfillment.dto.FulfillmentStatusResponse;

public interface FulfillmentApplicationService {
    /**
     * 确认支付并发放
     */
    FulfillmentResponse confirmPaidAndFulfill(ConfirmPaymentRequest request);
    
    /**
     * 重试发放
     */
    FulfillmentResponse retryByTaskNo(String taskNo);
    
    /**
     * 按订单号查询发放状态
     */
    FulfillmentStatusResponse queryByOrderNo(String bizOrderNo);
    
    /**
     * 按任务号查询发放详情（包含执行历史）
     */
    FulfillmentDetailResponse queryByTaskNo(String taskNo);
    
    /**
     * 手动补发（生成新任务）
     * 用于人工补偿场景，区别于重试（重试是复用原任务）
     */
    FulfillmentResponse manualFulfill(com.example.fulfillment.dto.ManualFulfillRequest request);

    /**
     * 按订单号和动作编码查询某次特定动作的发放任务状态
     * <p>
     * 与 {@link #queryByOrderNo(String)} 的区别：
     * <ul>
     *   <li>{@code queryByOrderNo} 返回"最近创建的那条任务"（不区分动作）</li>
     *   <li>{@code queryByOrderNoAndAction} 精确查询"某个订单的某次特定动作"</li>
     * </ul>
     * <p>
     * 适用场景：退款后想确认 REVOKE 是否成功、续费后确认 RENEW 结果。
     *
     * @param bizOrderNo 业务订单号
     * @param actionCode 动作编码（如 GRANT / REVOKE / RENEW），会自动归一化为大写
     * @return 匹配的最近一条任务状态；不存在则返回 null
     */
    FulfillmentStatusResponse queryByOrderNoAndAction(String bizOrderNo, String actionCode);

    /**
     * 查询当前权益状态（聚合该订单下某种权益类型的全部生命周期动作）
     * <p>
     * 本方法不替业务方定义"ACTIVE / REVOKED"语义，而是报告：
     * <ul>
     *   <li>最近一次<strong>成功</strong>完成的动作（lastSuccessfulAction）及其时间</li>
     *   <li>最新创建的任务（无论成功/失败）</li>
     *   <li>各状态任务的汇总计数</li>
     * </ul>
     * <p>
     * 宿主应用可根据 {@code lastSuccessfulAction} 推导业务含义，例如：
     * <pre>
     * if ("GRANT".equals(resp.getLastSuccessfulAction())) { /* 权益有效 *&#47; }
     * if ("REVOKE".equals(resp.getLastSuccessfulAction())) { /* 权益已撤销 *&#47; }
     * </pre>
     *
     * @param bizOrderNo      业务订单号
     * @param benefitTypeCode 权益类型编码
     * @return 聚合后的权益状态；如果该订单下不存在任何任务则返回 null
     */
    EntitlementStatusResponse queryCurrentEntitlement(String bizOrderNo, String benefitTypeCode);

    /**
     * 统一生命周期动作入口
     * <p>
     * 根据 {@code actionCode}（GRANT / REVOKE / RENEW 等）和 {@code benefitTypeCode}
     * 联合匹配 Handler 并执行。支持幂等控制、任务创建、日志记录、重试等完整链路。
     * <p>
     * 与 {@link #confirmPaidAndFulfill} 的区别：
     * <ul>
     *   <li>本方法不涉及支付记录写入</li>
     *   <li>本方法通过 actionCode 区分不同的生命周期操作</li>
     *   <li>适用于退款撤销、续费续期、运营授予等非支付触发的场景</li>
     * </ul>
     *
     * @param request 生命周期动作请求
     * @return 执行结果
     * @see com.example.fulfillment.common.enums.LifecycleActionCodes
     */
    FulfillmentResponse executeLifecycleAction(ExecuteLifecycleActionRequest request);

    /**
     * 批量自动重试（供宿主应用调度调用）
     * <p>
     * 扫描状态为 RETRY_WAIT 且重试次数未超限且退避时间已到的任务，逐个执行重试。
     * <p>
     * SDK 不内置调度器，由宿主应用自行决定调度方式：
     * <ul>
     *   <li>Spring @Scheduled 定时任务（单实例）</li>
     *   <li>XXL-JOB / Elastic-Job / Quartz Cluster（分布式）</li>
     *   <li>K8s CronJob（容器化）</li>
     * </ul>
     *
     * @return 本次成功重试的任务数量
     */
    int retryFailedTasks();

    /**
     * 恢复卡死的 PROCESSING 任务（供宿主应用调度调用）
     * <p>
     * 扫描状态为 PROCESSING 且 {@code updated_at} 超过配置的超时阈值
     * （{@code fulfillment.processing-timeout-minutes}，默认 30 分钟）的任务，
     * 将它们重置为 RETRY_WAIT，使其可以被 {@link #retryFailedTasks()} 再次拾取执行。
     * <p>
     * 适用场景：
     * <ul>
     *   <li>应用进程被 kill，导致正在执行的任务状态永远停在 PROCESSING</li>
     *   <li>Handler 发生死锁或无限阻塞（超出超时控制范围）</li>
     *   <li>网络分区导致数据库提交成功但 JVM 内线程中断</li>
     * </ul>
     * <p>
     * 建议与 {@link #retryFailedTasks()} 放在同一个调度周期内调用，例如：
     * <pre>
     * &#64;Scheduled(fixedDelay = 60000)
     * public void fulfillmentMaintenance() {
     *     fulfillmentService.recoverStuckTasks();
     *     fulfillmentService.retryFailedTasks();
     * }
     * </pre>
     *
     * @return 本次恢复的任务数量
     */
    int recoverStuckTasks();
}
