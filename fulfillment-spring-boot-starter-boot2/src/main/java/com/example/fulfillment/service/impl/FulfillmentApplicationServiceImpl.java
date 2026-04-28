package com.example.fulfillment.service.impl;

import com.example.fulfillment.common.enums.ErrorCode;
import com.example.fulfillment.common.enums.FulfillmentTaskStatus;
import com.example.fulfillment.common.enums.LifecycleActionCodes;
import com.example.fulfillment.common.exception.BizException;
import com.example.fulfillment.common.util.IdempotentKeyGenerator;
import com.example.fulfillment.common.util.StringUtils;
import com.example.fulfillment.config.FulfillmentProperties;
import com.example.fulfillment.core.repository.FulfillmentLogRepository;
import com.example.fulfillment.core.repository.FulfillmentTaskRepository;
import com.example.fulfillment.core.repository.IdempotentRecordRepository;
import com.example.fulfillment.domain.entity.FulfillmentLog;
import com.example.fulfillment.domain.entity.FulfillmentTask;
import com.example.fulfillment.domain.entity.IdempotentRecord;
import com.example.fulfillment.dto.ConfirmPaymentRequest;
import com.example.fulfillment.dto.EntitlementStatusResponse;
import com.example.fulfillment.dto.ExecuteLifecycleActionRequest;
import com.example.fulfillment.dto.FulfillmentDetailResponse;
import com.example.fulfillment.dto.FulfillmentLogItem;
import com.example.fulfillment.dto.FulfillmentResponse;
import com.example.fulfillment.dto.FulfillmentStatusResponse;
import com.example.fulfillment.dto.ManualFulfillRequest;
import com.example.fulfillment.service.FulfillmentApplicationService;
import com.example.fulfillment.service.FulfillmentTransactionService;
import com.example.fulfillment.service.handler.HandlerResult;
import com.example.fulfillment.spi.FulfillmentHandler;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 发放服务实现（核心引擎）
 * <p>
 * 企业级设计要点：
 * <ol>
 *   <li>事务拆分：任务创建与 Handler 执行分离，避免长事务</li>
 *   <li>乐观锁：任务状态更新携带 version，防止并发覆盖</li>
 *   <li>线程池管理：有界线程池 + 命名线程 + 优雅关闭</li>
 *   <li>幂等保障：自动生成幂等键，DuplicateKey 兜底</li>
 *   <li>安全截断：错误信息防止数据库字段溢出</li>
 *   <li>生命周期动作：通过 actionCode 区分 GRANT / REVOKE / RENEW 等动作</li>
 * </ol>
 * <p>
 * 由 {@link com.example.fulfillment.config.FulfillmentRuntimeAutoConfiguration} 自动装配。
 */
public class FulfillmentApplicationServiceImpl implements FulfillmentApplicationService {
    
    private static final Logger log = LoggerFactory.getLogger(FulfillmentApplicationServiceImpl.class);
    private static final String SCENE_PAY_CONFIRM = "PAY_CONFIRM";
    private static final String SCENE_MANUAL_FULFILL = "MANUAL_FULFILL";
    private static final String SCENE_LIFECYCLE_ACTION = "LIFECYCLE_ACTION";
    
    private final FulfillmentTransactionService transactionService;
    private final IdempotentRecordRepository idempotentRecordRepository;
    private final FulfillmentTaskRepository fulfillmentTaskRepository;
    private final FulfillmentLogRepository fulfillmentLogRepository;
    private final FulfillmentHandlerRegistry handlerRegistry;
    private final FulfillmentProperties properties;
    private final ExecutorService handlerExecutor;

    public FulfillmentApplicationServiceImpl(
            FulfillmentTransactionService transactionService,
            IdempotentRecordRepository idempotentRecordRepository,
            FulfillmentTaskRepository fulfillmentTaskRepository,
            FulfillmentLogRepository fulfillmentLogRepository,
            FulfillmentHandlerRegistry handlerRegistry,
            FulfillmentProperties properties) {
        this.transactionService = transactionService;
        this.idempotentRecordRepository = idempotentRecordRepository;
        this.fulfillmentTaskRepository = fulfillmentTaskRepository;
        this.fulfillmentLogRepository = fulfillmentLogRepository;
        this.handlerRegistry = handlerRegistry;
        this.properties = properties;

        // 有界线程池：命名线程 + 有限队列 + CallerRunsPolicy 降级
        this.handlerExecutor = new ThreadPoolExecutor(
                properties.getHandlerThreadPoolCoreSize(),
                properties.getHandlerThreadPoolMaxSize(),
                properties.getHandlerThreadPoolKeepAliveSeconds(), TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(properties.getHandlerThreadPoolQueueCapacity()),
                r -> {
                    Thread t = new Thread(r);
                    t.setName("fulfillment-handler-" + t.getId());
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        log.info("Fulfillment SDK 线程池初始化: core={}, max={}, queue={}",
                properties.getHandlerThreadPoolCoreSize(),
                properties.getHandlerThreadPoolMaxSize(),
                properties.getHandlerThreadPoolQueueCapacity());
        logOperationalWarnings();
    }

    @PreDestroy
    public void shutdown() {
        log.info("正在关闭 Fulfillment SDK 线程池...");
        handlerExecutor.shutdown();
        try {
            if (!handlerExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                handlerExecutor.shutdownNow();
                log.warn("线程池强制关闭");
            }
        } catch (InterruptedException e) {
            handlerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Fulfillment SDK 线程池已关闭");
    }

    // ==================== 支付确认发放（actionCode 默认 GRANT） ====================

    @Override
    public FulfillmentResponse confirmPaidAndFulfill(ConfirmPaymentRequest request) {
        log.info("收到支付确认请求: bizOrderNo={}, paymentNo={}, benefitTypeCode={}", 
                request.getBizOrderNo(), request.getPaymentNo(), request.getBenefitTypeCode());
        
        // 阶段 1：事务内创建任务（通过独立 Bean 调用，确保事务生效）
        FulfillmentTask task = transactionService.createTaskTransactional(request);
        if (task == null) {
            // 幂等命中，直接返回（还原与 createTaskTransactional 相同的 key 生成逻辑）
            String resolvedKey = resolvePayConfirmIdempotentKey(
                    request.getIdempotentKey(), request.getBizOrderNo(), request.getPaymentNo());
            return handleIdempotentReplay(SCENE_PAY_CONFIRM, resolvedKey);
        }
        
        // 阶段 2：事务外执行 Handler（避免长事务）
        return executeAndUpdateResult(task);
    }

    // ==================== 手动补发（actionCode 默认 GRANT） ====================

    @Override
    public FulfillmentResponse manualFulfill(ManualFulfillRequest request) {
        // 显式校验：手动补发要求调用方必须传入 idempotentKey
        if (request == null) {
            throw new BizException("INVALID_PARAMETER", "ManualFulfillRequest cannot be null");
        }
        String normalizedKey = StringUtils.normalizeIdempotentKey(request.getIdempotentKey());
        if (normalizedKey == null) {
            throw new BizException("INVALID_PARAMETER",
                    "manualFulfill requires explicit idempotentKey, but it is null or blank");
        }
        request.setIdempotentKey(normalizedKey); // 写回标准化后的值

        log.info("收到手动补发请求: bizOrderNo={}, benefitTypeCode={}, reason={}, idempotentKey={}",
                request.getBizOrderNo(), request.getBenefitTypeCode(), request.getReason(), normalizedKey);
        
        // 阶段 1：事务内创建任务
        FulfillmentTask task = transactionService.createManualTaskTransactional(request);
        if (task == null) {
            // 幂等命中（使用标准化后的 key）
            return handleIdempotentReplay(SCENE_MANUAL_FULFILL, normalizedKey);
        }
        
        // 阶段 2：事务外执行
        return executeAndUpdateResult(task);
    }

    // ==================== 统一生命周期动作入口 ====================

    @Override
    public FulfillmentResponse executeLifecycleAction(ExecuteLifecycleActionRequest request) {
        // 显式参数校验与归一化（不依赖 DTO 注解，确保程序调用时也能校验）
        validateAndNormalizeLifecycleRequest(request);

        log.info("收到生命周期动作请求: bizOrderNo={}, benefitTypeCode={}, actionCode={}, reason={}",
                request.getBizOrderNo(), request.getBenefitTypeCode(),
                request.getActionCode(), request.getReason());

        // 阶段 1：事务内创建任务
        FulfillmentTask task = transactionService.createLifecycleTaskTransactional(request);
        if (task == null) {
            // 幂等命中（还原与 createLifecycleTaskTransactional 相同的 key 生成逻辑）
            String resolvedKey = resolveLifecycleIdempotentKey(
                    request.getIdempotentKey(), request.getBizOrderNo(), request.getActionCode());
            return handleIdempotentReplay(SCENE_LIFECYCLE_ACTION, resolvedKey);
        }

        // 阶段 2：事务外执行 Handler
        return executeAndUpdateResult(task);
    }

    // ==================== 重试 ====================

    @Override
    public FulfillmentResponse retryByTaskNo(String taskNo) {
        log.info("收到重试请求: taskNo={}", taskNo);
        
        FulfillmentTask task = fulfillmentTaskRepository.findByTaskNo(taskNo);
        if (task == null) {
            throw new BizException("TASK_NOT_FOUND", "Task not found: " + taskNo);
        }
        
        if (FulfillmentTaskStatus.SUCCESS.name().equals(task.getStatus())) {
            throw new BizException("TASK_ALREADY_SUCCESS", "Task already succeeded, use manual-fulfill for reissue");
        }
        if (FulfillmentTaskStatus.PROCESSING.name().equals(task.getStatus())) {
            throw new BizException("TASK_PROCESSING", "Task is currently processing, please wait");
        }
        if (task.getRetryCount() >= task.getMaxRetryCount()) {
            throw new BizException("MAX_RETRY_EXCEEDED", "Max retry count exceeded: " 
                    + task.getRetryCount() + "/" + task.getMaxRetryCount());
        }
        if (!FulfillmentTaskStatus.RETRY_WAIT.name().equals(task.getStatus()) &&
                !FulfillmentTaskStatus.FAILED.name().equals(task.getStatus())) {
            throw new BizException("TASK_STATUS_NOT_RETRYABLE", "Status " + task.getStatus() + " is not retryable");
        }
        
        // 乐观锁更新为 PROCESSING（防止并发重试）
        task.setStatus(FulfillmentTaskStatus.PROCESSING.name());
        int updated = fulfillmentTaskRepository.updateStatusWithVersion(task);
        if (updated == 0) {
            throw new BizException("CONCURRENT_MODIFICATION", "Task has been modified by another request");
        }
        // 乐观锁成功，version 已在数据库 +1，同步到内存
        task.setVersion(task.getVersion() + 1);
        
        return executeAndUpdateResult(task);
    }

    // ==================== 批量自动重试 ====================

    @Override
    public int retryFailedTasks() {
        int batchSize = properties.getRetryBatchSize();
        List<FulfillmentTask> retryableTasks = fulfillmentTaskRepository.findRetryableTasks(batchSize);
        
        if (retryableTasks.isEmpty()) {
            log.debug("自动重试扫描：无可重试任务");
            return 0;
        }
        
        log.info("自动重试扫描：发现 {} 条可重试任务", retryableTasks.size());
        int successCount = 0;
        
        for (FulfillmentTask task : retryableTasks) {
            try {
                // 乐观锁抢占
                task.setStatus(FulfillmentTaskStatus.PROCESSING.name());
                int updated = fulfillmentTaskRepository.updateStatusWithVersion(task);
                if (updated == 0) {
                    log.debug("自动重试跳过（已被其他实例抢占）: taskNo={}", task.getTaskNo());
                    continue;
                }
                task.setVersion(task.getVersion() + 1);

                log.info("自动重试开始: taskNo={}, actionCode={}, retry={}/{}", 
                        task.getTaskNo(), task.getActionCode(),
                        task.getRetryCount(), task.getMaxRetryCount());
                
                FulfillmentResponse result = executeAndUpdateResult(task);
                if (result.isSuccess()) {
                    successCount++;
                }
            } catch (Exception e) {
                log.error("自动重试异常: taskNo={}", task.getTaskNo(), e);
            }
        }
        
        log.info("自动重试完成: 总计={}, 成功={}, 失败={}", 
                retryableTasks.size(), successCount, retryableTasks.size() - successCount);
        return successCount;
    }

    // ==================== PROCESSING 卡死自愈 ====================

    @Override
    public int recoverStuckTasks() {
        int timeoutMinutes = properties.getProcessingTimeoutMinutes();
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
        int batchSize = properties.getRetryBatchSize();

        List<FulfillmentTask> stuckTasks = fulfillmentTaskRepository
                .findStuckProcessingTasks(timeoutThreshold, batchSize);

        if (stuckTasks.isEmpty()) {
            log.debug("卡死任务扫描：无卡死的 PROCESSING 任务");
            return 0;
        }

        log.warn("卡死任务扫描：发现 {} 条疑似卡死任务（PROCESSING 超过 {} 分钟）",
                stuckTasks.size(), timeoutMinutes);

        int recoveredCount = 0;
        for (FulfillmentTask task : stuckTasks) {
            try {
                // 重置为 RETRY_WAIT，以便下次自动重试可以重新拾取
                task.setStatus(FulfillmentTaskStatus.RETRY_WAIT.name());
                task.setLastErrorCode("PROCESSING_TIMEOUT");
                task.setLastErrorMsg("Task stuck in PROCESSING for over " + timeoutMinutes
                        + " minutes, auto-recovered to RETRY_WAIT");
                // 设置退避时间，避免立刻又被扫描执行
                long backoffSeconds = properties.getRetryBackoffBaseSeconds();
                task.setNextRetryAt(LocalDateTime.now().plusSeconds(backoffSeconds));

                int updated = fulfillmentTaskRepository.updateStatusWithVersion(task);
                if (updated > 0) {
                    recoveredCount++;
                    log.info("卡死任务已恢复: taskNo={}, retry={}/{}, nextRetryAt={}",
                            task.getTaskNo(), task.getRetryCount(), task.getMaxRetryCount(),
                            task.getNextRetryAt());
                } else {
                    log.debug("卡死任务恢复跳过（乐观锁冲突，可能已被恢复）: taskNo={}", task.getTaskNo());
                }
            } catch (Exception e) {
                log.error("卡死任务恢复异常: taskNo={}", task.getTaskNo(), e);
            }
        }

        log.info("卡死任务恢复完成: 发现={}, 已恢复={}", stuckTasks.size(), recoveredCount);
        return recoveredCount;
    }

    // ==================== 查询 ====================

    /**
     * 按订单号查询最近一条发放任务的状态
     * <p>
     * <strong>注意：</strong>在生命周期动作场景下，同一订单号可能产生多条任务（GRANT / REVOKE / RENEW），
     * 此方法仅返回最后创建的那条任务的状态。如需按动作类型精确查询，建议使用 {@link #queryByTaskNo(String)}。
     */
    @Override
    @Transactional(readOnly = true)
    public FulfillmentStatusResponse queryByOrderNo(String bizOrderNo) {
        log.debug("查询发放状态: bizOrderNo={}", bizOrderNo);
        if (bizOrderNo == null || bizOrderNo.trim().isEmpty()) {
            throw new BizException("INVALID_PARAMETER", "Order number cannot be empty");
        }
        FulfillmentTask task = fulfillmentTaskRepository.findByBizOrderNo(bizOrderNo);
        if (task == null) {
            log.debug("未找到发放任务: bizOrderNo={}", bizOrderNo);
            return null;
        }
        return convertToStatusResponse(task);
    }

    @Override
    @Transactional(readOnly = true)
    public FulfillmentDetailResponse queryByTaskNo(String taskNo) {
        log.debug("查询发放详情: taskNo={}", taskNo);
        if (taskNo == null || taskNo.trim().isEmpty()) {
            throw new BizException("INVALID_PARAMETER", "Task number cannot be empty");
        }
        FulfillmentTask task = fulfillmentTaskRepository.findByTaskNo(taskNo);
        if (task == null) {
            throw new BizException("TASK_NOT_FOUND", "Task not found: " + taskNo);
        }
        FulfillmentDetailResponse response = new FulfillmentDetailResponse();
        convertToStatusResponse(task, response);
        List<FulfillmentLog> logs = fulfillmentLogRepository.findByTaskId(task.getId());
        response.setLogs(logs.stream().map(this::convertToLogItem).collect(Collectors.toList()));
        return response;
    }

    // ==================== 按订单号 + 动作查询 ====================

    @Override
    @Transactional(readOnly = true)
    public FulfillmentStatusResponse queryByOrderNoAndAction(String bizOrderNo, String actionCode) {
        log.debug("查询发放状态: bizOrderNo={}, actionCode={}", bizOrderNo, actionCode);
        if (bizOrderNo == null || bizOrderNo.trim().isEmpty()) {
            throw new BizException("INVALID_PARAMETER", "Order number cannot be empty");
        }
        if (actionCode == null || actionCode.trim().isEmpty()) {
            throw new BizException("INVALID_PARAMETER", "Action code cannot be empty");
        }
        // 归一化 actionCode（trim + uppercase），与写入时保持一致
        String normalizedAction;
        try {
            normalizedAction = StringUtils.normalizeActionCode(actionCode);
        } catch (IllegalArgumentException e) {
            throw new BizException("INVALID_PARAMETER", "Invalid actionCode: " + e.getMessage());
        }

        FulfillmentTask task = fulfillmentTaskRepository.findByBizOrderNoAndActionCode(bizOrderNo.trim(), normalizedAction);
        if (task == null) {
            log.debug("未找到发放任务: bizOrderNo={}, actionCode={}", bizOrderNo, normalizedAction);
            return null;
        }
        return convertToStatusResponse(task);
    }

    // ==================== 当前权益状态查询 ====================

    /**
     * 聚合某订单下某种权益的全部生命周期任务，提供权益概况
     * <p>
     * SDK 只报告"发生了什么"，不替业务方定义有效性语义。
     */
    @Override
    @Transactional(readOnly = true)
    public EntitlementStatusResponse queryCurrentEntitlement(String bizOrderNo, String benefitTypeCode) {
        log.debug("查询当前权益状态: bizOrderNo={}, benefitTypeCode={}", bizOrderNo, benefitTypeCode);
        if (bizOrderNo == null || bizOrderNo.trim().isEmpty()) {
            throw new BizException("INVALID_PARAMETER", "Order number cannot be empty");
        }
        if (benefitTypeCode == null || benefitTypeCode.trim().isEmpty()) {
            throw new BizException("INVALID_PARAMETER", "Benefit type code cannot be empty");
        }

        List<FulfillmentTask> tasks = fulfillmentTaskRepository
                .findAllByBizOrderNoAndBenefitTypeCode(bizOrderNo.trim(), benefitTypeCode.trim());
        if (tasks == null || tasks.isEmpty()) {
            log.debug("未找到任何发放任务: bizOrderNo={}, benefitTypeCode={}", bizOrderNo, benefitTypeCode);
            return null;
        }

        return buildEntitlementStatus(tasks, bizOrderNo.trim(), benefitTypeCode.trim());
    }

    /**
     * 从任务列表（按 created_at 倒序）聚合出权益状态
     */
    private EntitlementStatusResponse buildEntitlementStatus(List<FulfillmentTask> tasks,
                                                              String bizOrderNo,
                                                              String benefitTypeCode) {
        EntitlementStatusResponse resp = new EntitlementStatusResponse();
        resp.setBizOrderNo(bizOrderNo);
        resp.setBenefitTypeCode(benefitTypeCode);
        resp.setTotalTaskCount(tasks.size());

        // 第一条即为最新任务（列表已按 created_at 倒序）
        FulfillmentTask latest = tasks.get(0);
        resp.setBizUserRef(latest.getBizUserRef());
        resp.setLatestTaskNo(latest.getTaskNo());
        resp.setLatestActionCode(latest.getActionCode());
        resp.setLatestTaskStatus(latest.getStatus());
        resp.setLatestTaskTime(latest.getCreatedAt());

        // 遍历统计 + 找最近一次成功任务
        int successCount = 0;
        int pendingCount = 0;
        int failedCount = 0;
        FulfillmentTask lastSuccessful = null;

        for (FulfillmentTask task : tasks) {
            String status = task.getStatus();
            if (FulfillmentTaskStatus.SUCCESS.name().equals(status)) {
                successCount++;
                if (lastSuccessful == null) {
                    // 第一个碰到的 SUCCESS 就是最近一次成功（列表已倒序）
                    lastSuccessful = task;
                }
            } else if (FulfillmentTaskStatus.PROCESSING.name().equals(status)
                    || FulfillmentTaskStatus.RETRY_WAIT.name().equals(status)) {
                pendingCount++;
            } else if (FulfillmentTaskStatus.FAILED.name().equals(status)) {
                failedCount++;
            }
        }

        resp.setSuccessCount(successCount);
        resp.setPendingCount(pendingCount);
        resp.setFailedCount(failedCount);

        if (lastSuccessful != null) {
            resp.setLastSuccessfulAction(lastSuccessful.getActionCode());
            resp.setLastSuccessfulTaskNo(lastSuccessful.getTaskNo());
            resp.setLastSuccessfulTime(lastSuccessful.getUpdatedAt());
        }

        return resp;
    }

    // ==================== Handler 执行引擎 ====================

    /**
     * 执行 Handler 并更新结果
     * <p>
     * 使用 task.getActionCode() 进行联合 Handler 匹配。
     * 此方法在事务外调用，Handler 执行完成后在新事务中更新任务状态和日志。
     */
    private FulfillmentResponse executeAndUpdateResult(FulfillmentTask task) {
        LocalDateTime start = LocalDateTime.now();
        FulfillmentLog logRecord = new FulfillmentLog();
        logRecord.setTaskId(task.getId());
        logRecord.setAttemptNo((task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1);
        logRecord.setActionCode(task.getActionCode());
        logRecord.setStartTime(start);
        
        String actionCode = task.getActionCode();
        if (actionCode == null || actionCode.trim().isEmpty()) {
            actionCode = LifecycleActionCodes.GRANT;
        }
        
        log.info("开始执行发放: taskNo={}, attemptNo={}, benefitType={}, actionCode={}", 
                task.getTaskNo(), logRecord.getAttemptNo(), task.getBenefitTypeCode(), actionCode);
        
        try {
            // 1. 查找 Handler（使用 benefitTypeCode + actionCode 联合匹配）
            String benefitTypeCode = task.getBenefitTypeCode();
            if (benefitTypeCode == null || benefitTypeCode.trim().isEmpty()) {
                log.error("权益类型编码为空: taskNo={}", task.getTaskNo());
                return transactionService.saveFailureResult(task, logRecord, start, 
                        ErrorCode.INVALID_BENEFIT_TYPE.getCode(), 
                        "Benefit type code is null or blank", false);
            }
            
            FulfillmentHandler handler;
            try {
                handler = handlerRegistry.getHandler(benefitTypeCode, actionCode);
            } catch (IllegalArgumentException e) {
                log.error("未找到 Handler: benefitTypeCode={}, actionCode={}, taskNo={}",
                        benefitTypeCode, actionCode, task.getTaskNo());
                return transactionService.saveFailureResult(task, logRecord, start, 
                        ErrorCode.HANDLER_NOT_FOUND.getCode(), 
                        "No handler for type: " + benefitTypeCode + ", action: " + actionCode, false);
            }
            
            logRecord.setHandlerName(handler.getClass().getSimpleName());
            
            // 2. 执行 Handler（带超时控制）
            HandlerResult result = executeWithTimeout(handler, task);
            
            logRecord.setEndTime(LocalDateTime.now());
            logRecord.setDurationMs(Duration.between(start, logRecord.getEndTime()).toMillis());
            
            // 3. 处理结果
            if (result.isSuccess()) {
                return transactionService.saveSuccessResult(task, logRecord, result.getSummary());
            } else {
                ErrorCode ec = ErrorCode.fromCode(result.getErrorCode());
                return transactionService.saveFailureResult(task, logRecord, start, 
                        result.getErrorCode(), result.getErrorMessage(), ec.isRetryable());
            }
            
        } catch (TimeoutException e) {
            log.error("Handler 执行超时: taskNo={}, timeout={}s", 
                    task.getTaskNo(), properties.getHandlerTimeoutSeconds());
            logRecord.setHandlerName(logRecord.getHandlerName() != null ? logRecord.getHandlerName() : "UNKNOWN");
            return transactionService.saveFailureResult(task, logRecord, start, 
                    ErrorCode.HANDLER_TIMEOUT.getCode(), 
                    "Timeout after " + properties.getHandlerTimeoutSeconds() + "s", true);
            
        } catch (BizException e) {
            log.warn("业务异常: taskNo={}, code={}, msg={}", task.getTaskNo(), e.getCode(), e.getMessage());
            logRecord.setHandlerName(logRecord.getHandlerName() != null ? logRecord.getHandlerName() : "UNKNOWN");
            ErrorCode ec = ErrorCode.fromCode(e.getCode());
            return transactionService.saveFailureResult(task, logRecord, start, e.getCode(), e.getMessage(), ec.isRetryable());
            
        } catch (Exception e) {
            log.error("发放系统异常: taskNo={}", task.getTaskNo(), e);
            logRecord.setHandlerName(logRecord.getHandlerName() != null ? logRecord.getHandlerName() : "UNKNOWN");
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return transactionService.saveFailureResult(task, logRecord, start, 
                    ErrorCode.HANDLER_EXCEPTION.getCode(), errorMsg, true);
        }
    }

    /**
     * 带超时控制执行 Handler
     */
    private HandlerResult executeWithTimeout(FulfillmentHandler handler, FulfillmentTask task) 
            throws TimeoutException {
        if (properties.getHandlerTimeoutSeconds() <= 0) {
            return handler.fulfill(task);
        }
        
        Future<HandlerResult> future = handlerExecutor.submit(() -> handler.fulfill(task));
        try {
            return future.get(properties.getHandlerTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            boolean cancelled = future.cancel(true);
            if (!cancelled) {
                log.warn("Handler 超时后取消任务失败，可能仍在后台执行: taskNo={}, handler={}",
                        task.getTaskNo(), handler.getClass().getName());
            }
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof BizException) {
                throw (BizException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new RuntimeException(cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Handler execution interrupted", e);
        }
    }

    /**
     * 启动时输出关键运行约束，避免接入方遗漏调度与中断协作。
     */
    private void logOperationalWarnings() {
        if (properties.getHandlerTimeoutSeconds() > 0) {
            log.warn("已启用 Handler 超时控制（{}s）。请确保业务 Handler 可响应线程中断（如检查 Thread.interrupted()、避免长时间不可中断阻塞），否则超时后可能继续占用线程。",
                    properties.getHandlerTimeoutSeconds());
        }
        log.warn("SDK 不内置调度器。生产环境请由宿主系统定时调用 retryFailedTasks() 与 recoverStuckTasks()，否则失败重试与卡死自愈能力将受限。");
    }

    // ==================== 幂等重放 ====================

    /**
     * 幂等命中时，按已解析的 idempotentKey 查找已有任务并返回重放结果
     * <p>
     * 查找策略（消除并发空窗期）：
     * <ol>
     *   <li>优先通过 record.taskNo 查找任务（INSERT 时即绑定，无空窗期）</li>
     *   <li>向下兼容：旧版记录可能只有 taskId 没有 taskNo，退而使用 taskId 查找</li>
     *   <li>极端并发：如果另一线程事务尚未提交（记录可见但任务不可见），
     *       抛出 CONCURRENT_PROCESSING 异常提示调用方稍后重试</li>
     * </ol>
     *
     * @param scene              场景码
     * @param resolvedIdempotentKey 已解析/已确定的幂等键
     */
    private FulfillmentResponse handleIdempotentReplay(String scene, String resolvedIdempotentKey) {
        IdempotentRecord record = idempotentRecordRepository.findBySceneAndKey(scene, resolvedIdempotentKey);
        if (record == null) {
            throw new BizException("DATA_INCONSISTENCY",
                    "Idempotent record not found for key: " + resolvedIdempotentKey);
        }

        // 优先通过 taskNo 查找任务（v2 方案：INSERT 时即绑定 taskNo，无 LOCKED 空窗期）
        FulfillmentTask task = null;
        if (record.getTaskNo() != null && !record.getTaskNo().isEmpty()) {
            task = fulfillmentTaskRepository.findByTaskNo(record.getTaskNo());
        }

        // 向下兼容：旧版记录可能只有 taskId
        if (task == null && record.getTaskId() != null) {
            task = fulfillmentTaskRepository.findById(record.getTaskId());
        }

        if (task == null) {
            // 极端情况：另一线程的事务正在进行（已插入幂等记录但未提交任务），
            // 或旧版遗留的 LOCKED 记录（崩溃残留）
            log.warn("幂等记录存在但关联任务未找到: key={}, taskNo={}, taskId={}, status={}",
                    resolvedIdempotentKey, record.getTaskNo(), record.getTaskId(), record.getStatus());
            throw new BizException("CONCURRENT_PROCESSING",
                    "Request is being processed by another thread, please retry later. Key: " + resolvedIdempotentKey);
        }

        log.info("幂等重放: taskNo={}, status={}, idempotentKey={}",
                task.getTaskNo(), task.getStatus(), resolvedIdempotentKey);
        return FulfillmentResponse.replay(task.getTaskNo(), task.getStatus());
    }

    // ==================== 参数校验与归一化 ====================

    /**
     * 校验并归一化生命周期动作请求
     * <p>
     * 执行以下处理：
     * <ol>
     *   <li>校验必填字段（bizOrderNo, bizUserRef, benefitTypeCode, actionCode, benefitConfigSnapshot）</li>
     *   <li>归一化 actionCode（trim + uppercase）</li>
     *   <li>归一化后的值写回 request 对象，供后续使用</li>
     * </ol>
     * <p>
     * 注意：此方法不依赖 DTO 的 @NotBlank 注解，确保程序内部直接调用时也能校验。
     *
     * @param request 生命周期动作请求
     * @throws BizException 如果必填字段为空或 actionCode 无效
     */
    private void validateAndNormalizeLifecycleRequest(ExecuteLifecycleActionRequest request) {
        if (request == null) {
            throw new BizException("INVALID_PARAMETER", "ExecuteLifecycleActionRequest cannot be null");
        }

        // 校验必填字段
        if (request.getBizOrderNo() == null || request.getBizOrderNo().trim().isEmpty()) {
            throw new BizException("INVALID_PARAMETER", "bizOrderNo cannot be null or blank");
        }
        if (request.getBizUserRef() == null || request.getBizUserRef().trim().isEmpty()) {
            throw new BizException("INVALID_PARAMETER", "bizUserRef cannot be null or blank");
        }
        if (request.getBenefitTypeCode() == null || request.getBenefitTypeCode().trim().isEmpty()) {
            throw new BizException("INVALID_PARAMETER", "benefitTypeCode cannot be null or blank");
        }
        if (request.getBenefitConfigSnapshot() == null || request.getBenefitConfigSnapshot().trim().isEmpty()) {
            throw new BizException("INVALID_PARAMETER", "benefitConfigSnapshot cannot be null or blank");
        }

        // 归一化 actionCode（trim + uppercase + 非空校验）
        String normalizedActionCode;
        try {
            normalizedActionCode = StringUtils.normalizeActionCode(request.getActionCode());
        } catch (IllegalArgumentException e) {
            throw new BizException("INVALID_PARAMETER", "actionCode cannot be null or blank: " + e.getMessage());
        }

        // 写回归一化后的值
        request.setActionCode(normalizedActionCode);
        // 其他字段也做 trim（但不强制 uppercase，因为可能是业务自定义值）
        request.setBizOrderNo(request.getBizOrderNo().trim());
        request.setBizUserRef(request.getBizUserRef().trim());
        request.setBenefitTypeCode(request.getBenefitTypeCode().trim());
        request.setBenefitConfigSnapshot(request.getBenefitConfigSnapshot().trim());
        if (request.getReason() != null) {
            request.setReason(request.getReason().trim());
        }
    }

    // ==================== 幂等键解析 ====================

    /**
     * 解析支付确认场景的幂等键
     * <p>
     * 与 {@link FulfillmentTransactionServiceImpl#createTaskTransactional} 使用相同的生成规则。
     * <p>
     * 如果调用方显式提供了幂等键，会先做 trim() 标准化，避免因空格导致幂等重放不一致。
     */
    private String resolvePayConfirmIdempotentKey(String providedKey, String bizOrderNo, String paymentNo) {
        String normalized = StringUtils.normalizeIdempotentKey(providedKey);
        if (normalized != null) {
            return normalized;
        }
        return IdempotentKeyGenerator.generate(SCENE_PAY_CONFIRM, bizOrderNo, paymentNo);
    }

    /**
     * 解析生命周期动作场景的幂等键
     * <p>
     * 与 {@link FulfillmentTransactionServiceImpl#createLifecycleTaskTransactional} 使用相同的生成规则。
     * actionCode 参与哈希，保证同一订单的不同动作不会互相冲突。
     * <p>
     * 注意：传入的 actionCode 应该是已经归一化后的值（大写、无空格）。
     * <p>
     * 如果调用方显式提供了幂等键，会先做 trim() 标准化，避免因空格导致幂等重放不一致。
     */
    private String resolveLifecycleIdempotentKey(String providedKey, String bizOrderNo, String actionCode) {
        String normalized = StringUtils.normalizeIdempotentKey(providedKey);
        if (normalized != null) {
            return normalized;
        }
        return IdempotentKeyGenerator.generateLifecycle(SCENE_LIFECYCLE_ACTION, bizOrderNo, actionCode);
    }

    // ==================== 内部转换 ====================

    private FulfillmentStatusResponse convertToStatusResponse(FulfillmentTask task) {
        FulfillmentStatusResponse response = new FulfillmentStatusResponse();
        convertToStatusResponse(task, response);
        return response;
    }

    private void convertToStatusResponse(FulfillmentTask task, FulfillmentStatusResponse response) {
        response.setTaskNo(task.getTaskNo());
        response.setBizOrderNo(task.getBizOrderNo());
        response.setBizUserRef(task.getBizUserRef());
        response.setBenefitTypeCode(task.getBenefitTypeCode());
        response.setActionCode(task.getActionCode());
        response.setStatus(task.getStatus());
        response.setRetryCount(task.getRetryCount());
        response.setMaxRetryCount(task.getMaxRetryCount());
        response.setLastErrorCode(task.getLastErrorCode());
        response.setLastErrorMsg(task.getLastErrorMsg());
        response.setCreatedAt(task.getCreatedAt());
        response.setUpdatedAt(task.getUpdatedAt());
    }

    private FulfillmentLogItem convertToLogItem(FulfillmentLog logEntity) {
        FulfillmentLogItem item = new FulfillmentLogItem();
        item.setAttemptNo(logEntity.getAttemptNo());
        item.setActionCode(logEntity.getActionCode());
        item.setHandlerName(logEntity.getHandlerName());
        item.setStatus(logEntity.getStatus());
        item.setErrorCode(logEntity.getErrorCode());
        item.setErrorMsg(logEntity.getErrorMsg());
        item.setResultSummary(logEntity.getResultSummary());
        item.setStartTime(logEntity.getStartTime());
        item.setEndTime(logEntity.getEndTime());
        item.setDurationMs(logEntity.getDurationMs());
        item.setCreatedAt(logEntity.getCreatedAt());
        return item;
    }
}
