package com.example.fulfillment.service.impl;

import com.example.fulfillment.common.enums.ErrorCode;
import com.example.fulfillment.common.enums.FulfillmentTaskStatus;
import com.example.fulfillment.common.enums.IdempotentStatus;
import com.example.fulfillment.common.enums.LifecycleActionCodes;
import com.example.fulfillment.common.enums.PaymentRecordStatus;
import com.example.fulfillment.common.exception.BizException;
import com.example.fulfillment.common.util.IdempotentKeyGenerator;
import com.example.fulfillment.common.util.StringUtils;
import com.example.fulfillment.config.FulfillmentProperties;
import com.example.fulfillment.core.repository.FulfillmentLogRepository;
import com.example.fulfillment.core.repository.FulfillmentTaskRepository;
import com.example.fulfillment.core.repository.IdempotentRecordRepository;
import com.example.fulfillment.core.repository.PaymentRecordRepository;
import com.example.fulfillment.domain.entity.FulfillmentLog;
import com.example.fulfillment.domain.entity.FulfillmentTask;
import com.example.fulfillment.domain.entity.IdempotentRecord;
import com.example.fulfillment.domain.entity.PaymentRecord;
import com.example.fulfillment.dto.ConfirmPaymentRequest;
import com.example.fulfillment.dto.ExecuteLifecycleActionRequest;
import com.example.fulfillment.dto.FulfillmentResponse;
import com.example.fulfillment.dto.ManualFulfillRequest;
import com.example.fulfillment.service.FulfillmentTransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 事务服务实现
 * <p>
 * 所有带 @Transactional 的方法都在此类中，通过独立 Bean 调用确保事务生效。
 * <p>
 * <strong>幂等抢占机制（v2 — 消除 LOCKED 空窗期）：</strong>
 * <ol>
 *   <li>先生成 taskNo（在任何 DB 写入之前）</li>
 *   <li>一步到位插入幂等记录（状态 DONE + taskNo），唯一约束作为并发守卫</li>
 *   <li>创建发放任务（使用预生成的 taskNo）</li>
 *   <li>回填 taskId 到幂等记录（便于按 ID 快查）</li>
 * </ol>
 * 如果事务回滚，幂等记录和任务一起回滚，不会残留半成品状态。
 */
public class FulfillmentTransactionServiceImpl implements FulfillmentTransactionService {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentTransactionServiceImpl.class);
    private static final String SCENE_PAY_CONFIRM = "PAY_CONFIRM";
    private static final String SCENE_MANUAL_FULFILL = "MANUAL_FULFILL";
    private static final String SCENE_LIFECYCLE_ACTION = "LIFECYCLE_ACTION";

    private final PaymentRecordRepository paymentRecordRepository;
    private final IdempotentRecordRepository idempotentRecordRepository;
    private final FulfillmentTaskRepository fulfillmentTaskRepository;
    private final FulfillmentLogRepository fulfillmentLogRepository;
    private final FulfillmentProperties properties;

    public FulfillmentTransactionServiceImpl(
            PaymentRecordRepository paymentRecordRepository,
            IdempotentRecordRepository idempotentRecordRepository,
            FulfillmentTaskRepository fulfillmentTaskRepository,
            FulfillmentLogRepository fulfillmentLogRepository,
            FulfillmentProperties properties) {
        this.paymentRecordRepository = paymentRecordRepository;
        this.idempotentRecordRepository = idempotentRecordRepository;
        this.fulfillmentTaskRepository = fulfillmentTaskRepository;
        this.fulfillmentLogRepository = fulfillmentLogRepository;
        this.properties = properties;
    }

    // ==================== 支付确认 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FulfillmentTask createTaskTransactional(ConfirmPaymentRequest request) {
        // 1. 幂等键（统一标准化：trim + 空白归 null）
        String idempotentKey = StringUtils.normalizeIdempotentKey(request.getIdempotentKey());
        if (idempotentKey == null) {
            idempotentKey = IdempotentKeyGenerator.generate(SCENE_PAY_CONFIRM,
                    request.getBizOrderNo(), request.getPaymentNo());
            log.debug("自动生成幂等键: {}", idempotentKey);
        }
        request.setIdempotentKey(idempotentKey);

        // 2. 预生成任务编号（在幂等抢占前，确保 record + task 原子绑定）
        String taskNo = generateTaskNo();

        // 3. 幂等抢占（唯一键冲突 = 幂等命中）
        IdempotentRecord idempotentRecord = tryClaimIdempotent(
                SCENE_PAY_CONFIRM, idempotentKey, request.getBizOrderNo(), taskNo);
        if (idempotentRecord == null) {
            return null; // 幂等命中，由调用方做重放
        }

        // 4. 写入支付记录
        PaymentRecord payment = paymentRecordRepository.findByPaymentNo(request.getPaymentNo());
        if (payment == null) {
            payment = new PaymentRecord();
            payment.setBizOrderNo(request.getBizOrderNo());
            payment.setPaymentNo(request.getPaymentNo());
            payment.setChannelCode(request.getChannelCode());
            payment.setPaidAmount(request.getPaidAmount());
            payment.setStatus(PaymentRecordStatus.RECEIVED.name());
            paymentRecordRepository.save(payment);
            log.debug("创建支付记录: paymentNo={}", request.getPaymentNo());
        }

        // 5. 创建发放任务（使用预生成的 taskNo，actionCode 默认 GRANT）
        FulfillmentTask task = buildTask(taskNo, request.getBizOrderNo(), request.getBizUserRef(),
                request.getBenefitTypeCode(), LifecycleActionCodes.GRANT,
                request.getBenefitConfigSnapshot());
        fulfillmentTaskRepository.save(task);
        log.info("创建发放任务: taskNo={}, bizOrderNo={}, actionCode={}",
                task.getTaskNo(), request.getBizOrderNo(), task.getActionCode());

        // 6. 回填 taskId 到幂等记录（便于按 ID 快查，status 仍为 DONE）
        idempotentRecord.setTaskId(task.getId());
        idempotentRecordRepository.updateStatusAndTask(idempotentRecord);

        return task;
    }

    // ==================== 手动补发 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FulfillmentTask createManualTaskTransactional(ManualFulfillRequest request) {
        // 防御性校验 + 标准化：手动补发要求显式传入 idempotentKey
        String idempotentKey = StringUtils.normalizeIdempotentKey(request.getIdempotentKey());
        if (idempotentKey == null) {
            throw new BizException("INVALID_PARAMETER",
                    "manualFulfill requires explicit idempotentKey, but it is null or blank");
        }
        request.setIdempotentKey(idempotentKey);

        // 预生成任务编号
        String taskNo = generateTaskNo();

        // 幂等抢占
        IdempotentRecord idempotentRecord = tryClaimIdempotent(
                SCENE_MANUAL_FULFILL, idempotentKey, request.getBizOrderNo(), taskNo);
        if (idempotentRecord == null) {
            return null; // 幂等命中
        }

        // 创建新任务（actionCode 默认 GRANT）
        FulfillmentTask task = buildTask(taskNo, request.getBizOrderNo(), request.getBizUserRef(),
                request.getBenefitTypeCode(), LifecycleActionCodes.GRANT,
                request.getBenefitConfigSnapshot());
        fulfillmentTaskRepository.save(task);
        log.info("创建补发任务: taskNo={}, bizOrderNo={}, reason={}, actionCode={}",
                task.getTaskNo(), request.getBizOrderNo(), request.getReason(), task.getActionCode());

        // 回填 taskId
        idempotentRecord.setTaskId(task.getId());
        idempotentRecordRepository.updateStatusAndTask(idempotentRecord);

        return task;
    }

    // ==================== 生命周期动作 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FulfillmentTask createLifecycleTaskTransactional(ExecuteLifecycleActionRequest request) {
        // 防御性校验：确保 actionCode 已归一化
        String originalActionCode = request.getActionCode();
        String normalizedActionCode;
        try {
            normalizedActionCode = StringUtils.normalizeActionCode(originalActionCode);
        } catch (IllegalArgumentException e) {
            throw new BizException("INVALID_PARAMETER", "actionCode validation failed: " + e.getMessage());
        }
        if (!normalizedActionCode.equals(originalActionCode)) {
            request.setActionCode(normalizedActionCode);
            log.debug("防御性归一化 actionCode: {} -> {}", originalActionCode, normalizedActionCode);
        }

        // 幂等键：统一标准化后，优先使用调用方显式传入，否则自动生成
        String idempotentKey = StringUtils.normalizeIdempotentKey(request.getIdempotentKey());
        if (idempotentKey == null) {
            idempotentKey = IdempotentKeyGenerator.generateLifecycle(
                    SCENE_LIFECYCLE_ACTION, request.getBizOrderNo(), normalizedActionCode);
            log.debug("自动生成生命周期幂等键: actionCode={}, bizOrderNo={}, key={}",
                    normalizedActionCode, request.getBizOrderNo(), idempotentKey);
        }
        request.setIdempotentKey(idempotentKey);

        // 预生成任务编号
        String taskNo = generateTaskNo();

        // 幂等抢占
        IdempotentRecord idempotentRecord = tryClaimIdempotent(
                SCENE_LIFECYCLE_ACTION, idempotentKey, request.getBizOrderNo(), taskNo);
        if (idempotentRecord == null) {
            return null; // 幂等命中
        }

        // 创建任务（携带归一化后的 actionCode）
        FulfillmentTask task = buildTask(taskNo, request.getBizOrderNo(), request.getBizUserRef(),
                request.getBenefitTypeCode(), normalizedActionCode,
                request.getBenefitConfigSnapshot());
        fulfillmentTaskRepository.save(task);
        log.info("创建生命周期任务: taskNo={}, bizOrderNo={}, actionCode={}, reason={}",
                task.getTaskNo(), request.getBizOrderNo(), normalizedActionCode, request.getReason());

        // 回填 taskId
        idempotentRecord.setTaskId(task.getId());
        idempotentRecordRepository.updateStatusAndTask(idempotentRecord);

        return task;
    }

    // ==================== 结果保存 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FulfillmentResponse saveSuccessResult(FulfillmentTask task, FulfillmentLog logRecord, String summary) {
        String truncatedSummary = StringUtils.truncateWithEllipsis(summary, properties.getMaxResultSummaryLength());

        task.setStatus(FulfillmentTaskStatus.SUCCESS.name());
        task.setLastErrorCode(null);
        task.setLastErrorMsg(null);
        task.setNextRetryAt(null);
        int updated = fulfillmentTaskRepository.updateStatusWithVersion(task);
        if (updated == 0) {
            log.warn("乐观锁冲突（成功更新时），taskNo={}", task.getTaskNo());
        }

        logRecord.setStatus("SUCCESS");
        logRecord.setResultSummary(truncatedSummary);
        fulfillmentLogRepository.save(logRecord);

        log.info("发放成功: taskNo={}, actionCode={}, duration={}ms",
                task.getTaskNo(), task.getActionCode(), logRecord.getDurationMs());
        return FulfillmentResponse.success(task.getTaskNo(), truncatedSummary);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FulfillmentResponse saveFailureResult(FulfillmentTask task, FulfillmentLog logRecord,
                                                 LocalDateTime start, String errorCode,
                                                 String errorMessage, boolean retryable) {
        logRecord.setEndTime(LocalDateTime.now());
        logRecord.setDurationMs(Duration.between(start, logRecord.getEndTime()).toMillis());

        int nextRetry = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
        task.setRetryCount(nextRetry);
        task.setLastErrorCode(errorCode);

        String truncatedMsg = StringUtils.truncateWithEllipsis(errorMessage, properties.getMaxErrorMessageLength());
        task.setLastErrorMsg(truncatedMsg);

        boolean canRetry = retryable && nextRetry < task.getMaxRetryCount();
        if (canRetry) {
            task.setStatus(FulfillmentTaskStatus.RETRY_WAIT.name());
            // 指数退避：baseSeconds * 2^(retryCount-1)
            long backoffSeconds = properties.getRetryBackoffBaseSeconds() * (1L << Math.min(nextRetry - 1, 10));
            task.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffSeconds));
            log.debug("设置下次重试时间: taskNo={}, nextRetryAt={}, backoff={}s",
                    task.getTaskNo(), task.getNextRetryAt(), backoffSeconds);
        } else {
            task.setStatus(FulfillmentTaskStatus.FAILED.name());
            task.setNextRetryAt(null);
        }

        int updated = fulfillmentTaskRepository.updateStatusWithVersion(task);
        if (updated == 0) {
            log.warn("乐观锁冲突（失败更新时），taskNo={}, 当前状态可能已被其他线程修改", task.getTaskNo());
            FulfillmentTask latest = fulfillmentTaskRepository.findByTaskNo(task.getTaskNo());
            if (latest != null) {
                return FulfillmentResponse.fail(latest.getTaskNo(), latest.getStatus(), errorCode, truncatedMsg);
            }
        }

        logRecord.setStatus("FAIL");
        logRecord.setErrorCode(errorCode);
        logRecord.setErrorMsg(truncatedMsg);
        fulfillmentLogRepository.save(logRecord);

        log.warn("发放失败: taskNo={}, actionCode={}, errorCode={}, retryable={}, nextRetry={}/{}",
                task.getTaskNo(), task.getActionCode(), errorCode, retryable, nextRetry, task.getMaxRetryCount());
        return FulfillmentResponse.fail(task.getTaskNo(), task.getStatus(), errorCode, truncatedMsg);
    }

    // ==================== 内部工具方法 ====================

    /**
     * 生成任务编号
     */
    private String generateTaskNo() {
        return "FT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 构建发放任务实体（使用预生成的 taskNo）
     */
    private FulfillmentTask buildTask(String taskNo, String bizOrderNo, String bizUserRef,
                                      String benefitTypeCode, String actionCode,
                                      String benefitConfigSnapshot) {
        FulfillmentTask task = new FulfillmentTask();
        task.setTaskNo(taskNo);
        task.setBizOrderNo(bizOrderNo);
        task.setBizUserRef(bizUserRef);
        task.setBenefitTypeCode(benefitTypeCode);
        task.setActionCode(actionCode);
        task.setBenefitConfigSnapshot(benefitConfigSnapshot);
        task.setStatus(FulfillmentTaskStatus.PROCESSING.name());
        task.setRetryCount(0);
        task.setMaxRetryCount(properties.getDefaultMaxRetryCount());
        task.setVersion(0);
        return task;
    }

    /**
     * 幂等抢占（原子操作，消除 LOCKED 空窗期）
     * <p>
     * 核心设计：
     * <ul>
     *   <li>INSERT 时直接设置 status=DONE + taskNo，不经过 LOCKED 中间态</li>
     *   <li>唯一索引 (scene_code, idempotent_key) 作为并发守卫</li>
     *   <li>如果事务最终回滚，幂等记录也一起回滚，不会残留半成品</li>
     *   <li>重复请求通过前置 SELECT 快速命中，避免不必要的 INSERT 异常</li>
     * </ul>
     *
     * @param sceneCode    场景码
     * @param idempotentKey 幂等键
     * @param bizOrderNo   业务订单号
     * @param taskNo       预生成的任务编号
     * @return 抢占成功返回新插入的 IdempotentRecord；幂等命中返回 null
     */
    private IdempotentRecord tryClaimIdempotent(String sceneCode, String idempotentKey,
                                                 String bizOrderNo, String taskNo) {
        // 快速检查：如果已存在记录（不论状态），直接视为幂等命中
        // 这是优化路径，减少不必要的 INSERT 异常开销
        IdempotentRecord existing = idempotentRecordRepository.findBySceneAndKey(sceneCode, idempotentKey);
        if (existing != null) {
            log.info("幂等命中（已有记录），跳过创建: sceneCode={}, idempotentKey={}, existingTaskNo={}",
                    sceneCode, idempotentKey, existing.getTaskNo());
            return null;
        }

        // 尝试插入：唯一约束是并发下的最终守卫
        try {
            IdempotentRecord record = new IdempotentRecord();
            record.setSceneCode(sceneCode);
            record.setIdempotentKey(idempotentKey);
            record.setBizOrderNo(bizOrderNo);
            record.setTaskNo(taskNo);
            record.setStatus(IdempotentStatus.DONE.name());
            record.setExpiredAt(LocalDateTime.now().plusHours(properties.getIdempotentExpireHours()));
            idempotentRecordRepository.save(record);
            log.debug("幂等抢占成功: sceneCode={}, idempotentKey={}, taskNo={}", sceneCode, idempotentKey, taskNo);
            return record;
        } catch (DataIntegrityViolationException e) {
            // 唯一索引冲突 = 并发请求已抢先插入，一律视为幂等命中
            log.info("幂等抢占失败（并发冲突）: sceneCode={}, idempotentKey={}", sceneCode, idempotentKey);
            return null;
        }
    }
}
