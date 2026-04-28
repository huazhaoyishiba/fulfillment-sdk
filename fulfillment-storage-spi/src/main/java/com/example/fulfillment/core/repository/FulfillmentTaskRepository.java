package com.example.fulfillment.core.repository;

import com.example.fulfillment.domain.entity.FulfillmentTask;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发放任务仓储接口（核心层）
 * <p>
 * 不绑定具体 ORM 实现。接入方可以使用 MyBatis、JdbcTemplate、JPA 等实现此接口。
 */
public interface FulfillmentTaskRepository {
    
    /**
     * 根据任务号查询
     */
    FulfillmentTask findByTaskNo(String taskNo);
    
    /**
     * 根据ID查询
     */
    FulfillmentTask findById(Long id);
    
    /**
     * 根据业务订单号查询最近创建的一条任务
     * <p>
     * <strong>注意：</strong>在生命周期动作场景下，同一订单号可能产生多条任务
     * （如 GRANT → RENEW → REVOKE），此方法仅返回最后创建的那条。
     * 如需按动作类型精确查询，应使用 {@code findByTaskNo} 或在宿主项目中扩展查询接口。
     *
     * @param bizOrderNo 业务订单号
     * @return 最近创建的任务；不存在则返回 null
     */
    FulfillmentTask findByBizOrderNo(String bizOrderNo);
    
    /**
     * 保存任务
     */
    void save(FulfillmentTask task);
    
    /**
     * 带乐观锁更新任务状态
     * <p>
     * 实现时必须使用 WHERE id = ? AND version = ? 进行更新，
     * 更新成功时将 version + 1。
     * 
     * @return 受影响行数。0 表示乐观锁冲突（其他线程已修改）
     */
    int updateStatusWithVersion(FulfillmentTask task);

    /**
     * 根据业务订单号和动作编码查询最近创建的一条任务
     * <p>
     * 用于精确查询某个订单的某次特定生命周期动作（如 GRANT / REVOKE / RENEW）。
     *
     * @param bizOrderNo 业务订单号
     * @param actionCode 动作编码（应传入归一化后的大写值）
     * @return 最近创建的匹配任务；不存在则返回 null
     */
    FulfillmentTask findByBizOrderNoAndActionCode(String bizOrderNo, String actionCode);

    /**
     * 查询某个订单下某种权益类型的所有任务（按创建时间倒序）
     * <p>
     * 用于聚合该订单的全部生命周期动作，进而判断当前权益的有效状态。
     * <p>
     * <strong>数据库兼容性注意：</strong>默认 MyBatis 实现使用标准 SQL，无 LIMIT 限制，
     * 适用于所有主流数据库。
     *
     * @param bizOrderNo     业务订单号
     * @param benefitTypeCode 权益类型编码
     * @return 任务列表（按 created_at 倒序）；无匹配时返回空列表
     */
    List<FulfillmentTask> findAllByBizOrderNoAndBenefitTypeCode(String bizOrderNo, String benefitTypeCode);

    /**
     * 查询可重试的任务列表
     * <p>
     * 条件：状态为 RETRY_WAIT 且 retry_count &lt; max_retry_count
     * 且 next_retry_at 为空或已到期（退避时间已过）。
     * <p>
     * 实现建议：加 LIMIT 限制单次批量大小，避免一次加载过多任务。
     * <p>
     * <strong>数据库兼容性注意：</strong>默认 MyBatis 实现使用 {@code LIMIT} 语法，
     * 适用于 MySQL / PostgreSQL / KingbaseES。Oracle / 达梦用户可能需要自定义 Repository
     * 实现以使用 {@code ROWNUM} 或 {@code FETCH FIRST} 语法。
     *
     * @param limit 最大返回条数
     * @return 可重试的任务列表
     */
    List<FulfillmentTask> findRetryableTasks(int limit);

    /**
     * 查询卡死的 PROCESSING 任务
     * <p>
     * 条件：状态为 PROCESSING 且 updated_at 早于 {@code timeoutThreshold}（即超过指定时间仍在 PROCESSING）。
     * <p>
     * 用于发现"机器挂了 / 进程 killed / handler 卡死"等异常场景下的残留任务，
     * 由 {@link com.example.fulfillment.service.FulfillmentApplicationService#recoverStuckTasks()} 驱动自愈。
     *
     * @param timeoutThreshold 超时阈值（updated_at 早于此时间的任务视为卡死）
     * @param limit            最大返回条数
     * @return 卡死的 PROCESSING 任务列表
     */
    List<FulfillmentTask> findStuckProcessingTasks(LocalDateTime timeoutThreshold, int limit);
}
