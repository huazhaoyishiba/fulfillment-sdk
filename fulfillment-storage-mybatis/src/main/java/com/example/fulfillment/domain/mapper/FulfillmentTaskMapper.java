package com.example.fulfillment.domain.mapper;

import com.example.fulfillment.domain.entity.FulfillmentTask;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 发放任务 MyBatis Mapper
 * <p>
 * <strong>数据库兼容性：</strong>本 Mapper 使用 {@code LIMIT} 语法，
 * 适用于 MySQL / PostgreSQL / KingbaseES / H2。
 * Oracle / 达梦用户如遇兼容问题，可自行实现 {@code FulfillmentTaskRepository}
 * 接口并替换此默认实现。
 */
@Mapper
public interface FulfillmentTaskMapper {

    @Insert("insert into sdk_fulfillment_task(task_no, biz_order_no, biz_user_ref, benefit_type_code, " +
            "action_code, benefit_config_snapshot, status, retry_count, max_retry_count, " +
            "last_error_code, last_error_msg, next_retry_at, version, created_at, updated_at) " +
            "values(#{task.taskNo}, #{task.bizOrderNo}, #{task.bizUserRef}, #{task.benefitTypeCode}, " +
            "#{task.actionCode}, #{task.benefitConfigSnapshot}, #{task.status}, #{task.retryCount}, #{task.maxRetryCount}, " +
            "#{task.lastErrorCode}, #{task.lastErrorMsg}, #{task.nextRetryAt}, 0, now(), now())")
    @Options(useGeneratedKeys = true, keyProperty = "task.id")
    int insert(@Param("task") FulfillmentTask task);

    @Select("select * from sdk_fulfillment_task where task_no = #{taskNo}")
    FulfillmentTask findByTaskNo(@Param("taskNo") String taskNo);

    @Select("select * from sdk_fulfillment_task where id = #{id}")
    FulfillmentTask findById(@Param("id") Long id);

    @Select("select * from sdk_fulfillment_task where biz_order_no = #{bizOrderNo} order by created_at desc limit 1")
    FulfillmentTask findByBizOrderNo(@Param("bizOrderNo") String bizOrderNo);

    @Select("select * from sdk_fulfillment_task " +
            "where biz_order_no = #{bizOrderNo} and action_code = #{actionCode} " +
            "order by created_at desc limit 1")
    FulfillmentTask findByBizOrderNoAndActionCode(@Param("bizOrderNo") String bizOrderNo,
                                                   @Param("actionCode") String actionCode);

    @Select("select * from sdk_fulfillment_task " +
            "where biz_order_no = #{bizOrderNo} and benefit_type_code = #{benefitTypeCode} " +
            "order by created_at desc")
    List<FulfillmentTask> findAllByBizOrderNoAndBenefitTypeCode(@Param("bizOrderNo") String bizOrderNo,
                                                                 @Param("benefitTypeCode") String benefitTypeCode);

    /**
     * 带乐观锁的状态更新
     * WHERE 条件中包含 version = #{task.version}，更新时 version + 1
     * 返回值为 0 表示版本冲突
     */
    @Update("update sdk_fulfillment_task " +
            "set status = #{task.status}, " +
            "retry_count = #{task.retryCount}, " +
            "last_error_code = #{task.lastErrorCode}, " +
            "last_error_msg = #{task.lastErrorMsg}, " +
            "next_retry_at = #{task.nextRetryAt}, " +
            "version = version + 1, " +
            "updated_at = now() " +
            "where id = #{task.id} and version = #{task.version}")
    int updateStatusWithVersion(@Param("task") FulfillmentTask task);

    /**
     * 查询可重试的任务
     * 条件：状态为 RETRY_WAIT 且 retry_count < max_retry_count
     * 且 next_retry_at 为空或已到期（退避时间已过）
     */
    @Select("select * from sdk_fulfillment_task " +
            "where status = 'RETRY_WAIT' " +
            "and retry_count < max_retry_count " +
            "and (next_retry_at is null or next_retry_at <= now()) " +
            "order by updated_at asc " +
            "limit #{limit}")
    List<FulfillmentTask> findRetryableTasks(@Param("limit") int limit);

    /**
     * 查询卡死的 PROCESSING 任务
     * 条件：状态为 PROCESSING 且 updated_at 早于指定的超时阈值
     */
    @Select("select * from sdk_fulfillment_task " +
            "where status = 'PROCESSING' " +
            "and updated_at < #{timeoutThreshold} " +
            "order by updated_at asc " +
            "limit #{limit}")
    List<FulfillmentTask> findStuckProcessingTasks(@Param("timeoutThreshold") LocalDateTime timeoutThreshold,
                                                    @Param("limit") int limit);
}
