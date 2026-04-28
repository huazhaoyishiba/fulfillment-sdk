package com.example.fulfillment;

import com.example.fulfillment.common.enums.FulfillmentTaskStatus;
import com.example.fulfillment.common.enums.LifecycleActionCodes;
import com.example.fulfillment.common.exception.BizException;
import com.example.fulfillment.config.FulfillmentProperties;
import com.example.fulfillment.dto.ConfirmPaymentRequest;
import com.example.fulfillment.dto.ExecuteLifecycleActionRequest;
import com.example.fulfillment.dto.FulfillmentDetailResponse;
import com.example.fulfillment.dto.EntitlementStatusResponse;
import com.example.fulfillment.dto.FulfillmentResponse;
import com.example.fulfillment.dto.FulfillmentStatusResponse;
import com.example.fulfillment.dto.ManualFulfillRequest;
import com.example.fulfillment.service.FulfillmentApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SDK 核心服务集成测试
 * <p>
 * 使用 H2 内存数据库 + MyBatis，验证完整的发放流程。
 * 包含支付确认发放、手动补发、重试、查询以及生命周期动作（GRANT / REVOKE / RENEW）测试。
 */
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
class FulfillmentServiceIntegrationTest {

    @Autowired
    private FulfillmentApplicationService fulfillmentService;

    @Autowired
    private JdbcTemplate jdbcTemplate; // 用于清空数据库

    @Autowired
    private FulfillmentProperties properties; // 用于修改配置以测试自动重试

    @BeforeEach
    void setUp() {
        // 清空所有业务相关的表，确保每个测试用例的独立性
        // 注意：先清日志表（无外键依赖），再清其他表
        jdbcTemplate.execute("TRUNCATE TABLE sdk_fulfillment_log");
        jdbcTemplate.execute("TRUNCATE TABLE sdk_fulfillment_task");
        jdbcTemplate.execute("TRUNCATE TABLE sdk_idempotent_record");
        jdbcTemplate.execute("TRUNCATE TABLE sdk_payment_record");
    }

    // ==================== 支付确认发放 ====================

    @Test
    @DisplayName("支付确认发放 - 正常成功")
    void confirmPaidAndFulfill_success() {
        ConfirmPaymentRequest request = buildConfirmRequest("ORDER-001", "PAY-001", "MEMBERSHIP");

        FulfillmentResponse response = fulfillmentService.confirmPaidAndFulfill(request);

        assertNotNull(response);
        assertTrue(response.isSuccess(), "发放应该成功");
        assertEquals("SUCCESS", response.getStatus());
        assertNotNull(response.getTaskNo(), "应返回任务编号");
        assertNotNull(response.getMessage(), "应返回结果摘要");
        assertNull(response.getErrorCode(), "成功时不应有 errorCode");
    }

    @Test
    @DisplayName("支付确认发放 - 幂等重放")
    void confirmPaidAndFulfill_idempotentReplay() {
        ConfirmPaymentRequest request = buildConfirmRequest("ORDER-002", "PAY-002", "MEMBERSHIP");
        request.setIdempotentKey("IDEM-FIXED-KEY");

        // 第一次调用
        FulfillmentResponse first = fulfillmentService.confirmPaidAndFulfill(request);
        assertTrue(first.isSuccess());

        // 第二次调用（同一幂等键）
        FulfillmentResponse second = fulfillmentService.confirmPaidAndFulfill(request);
        assertNotNull(second);
        assertEquals(first.getTaskNo(), second.getTaskNo(), "幂等重放应返回同一任务编号");
        assertEquals("idempotent replay", second.getMessage());
    }

    @Test
    @DisplayName("支付确认发放 - Handler 未找到")
    void confirmPaidAndFulfill_handlerNotFound() {
        // CUSTOM 类型没有注册对应的 Handler
        ConfirmPaymentRequest request = buildConfirmRequest("ORDER-003", "PAY-003", "CUSTOM");

        FulfillmentResponse response = fulfillmentService.confirmPaidAndFulfill(request);

        assertNotNull(response);
        assertFalse(response.isSuccess(), "应该失败");
        assertEquals("FAILED", response.getStatus(), "Handler 找不到应直接失败，不可重试");
        assertEquals("HANDLER_NOT_FOUND", response.getErrorCode());
    }

    @Test
    @DisplayName("支付确认发放 - API_QUOTA 权益类型")
    void confirmPaidAndFulfill_apiQuota() {
        ConfirmPaymentRequest request = buildConfirmRequest("ORDER-004", "PAY-004", "API_QUOTA");

        FulfillmentResponse response = fulfillmentService.confirmPaidAndFulfill(request);

        assertTrue(response.isSuccess());
        assertEquals("SUCCESS", response.getStatus());
    }

    @Test
    @DisplayName("支付确认发放 - 默认 actionCode 为 GRANT")
    void confirmPaidAndFulfill_defaultActionIsGrant() {
        ConfirmPaymentRequest request = buildConfirmRequest("ORDER-010", "PAY-010", "MEMBERSHIP");

        FulfillmentResponse response = fulfillmentService.confirmPaidAndFulfill(request);
        assertTrue(response.isSuccess());

        // 查询状态，验证 actionCode 默认为 GRANT
        FulfillmentStatusResponse status = fulfillmentService.queryByOrderNo("ORDER-010");
        assertNotNull(status);
        assertEquals(LifecycleActionCodes.GRANT, status.getActionCode(), "支付确认发放的 actionCode 应默认为 GRANT");
    }

    // ==================== 重试 ====================

    @Test
    @DisplayName("重试 - 任务不存在")
    void retryByTaskNo_notFound() {
        BizException ex = assertThrows(BizException.class, () ->
                fulfillmentService.retryByTaskNo("NON-EXISTENT-TASK"));
        assertEquals("TASK_NOT_FOUND", ex.getCode());
    }

    @Test
    @DisplayName("重试 - 已成功的任务不允许重试")
    void retryByTaskNo_alreadySuccess() {
        ConfirmPaymentRequest request = buildConfirmRequest("ORDER-005", "PAY-005", "MEMBERSHIP");
        FulfillmentResponse first = fulfillmentService.confirmPaidAndFulfill(request);
        assertTrue(first.isSuccess());

        BizException ex = assertThrows(BizException.class, () ->
                fulfillmentService.retryByTaskNo(first.getTaskNo()));
        assertEquals("TASK_ALREADY_SUCCESS", ex.getCode());
    }

    @Test
    @DisplayName("自动重试 - Flaky Handler 最终成功")
    void autoRetry_flakyHandlerEventuallySucceeds() {
        // 1. 模拟第一次发放失败
        ConfirmPaymentRequest request = buildConfirmRequest("ORDER-RETRY-001", "PAY-RETRY-001", "FILE_ACCESS");
        FulfillmentResponse initialResponse = fulfillmentService.confirmPaidAndFulfill(request);
        assertFalse(initialResponse.isSuccess());
        assertEquals(FulfillmentTaskStatus.RETRY_WAIT.name(), initialResponse.getStatus());
        assertEquals("DOWNSTREAM_UNAVAILABLE", initialResponse.getErrorCode());

        // 2. 手动调用重试
        properties.setRetryBatchSize(1); // 每次只处理一个
        int retriedCount = fulfillmentService.retryFailedTasks(); // 宿主自行调用

        assertEquals(1, retriedCount, "应该重试 1 条任务");

        // 3. 验证任务最终状态
        FulfillmentStatusResponse finalStatus = fulfillmentService.queryByOrderNo("ORDER-RETRY-001");
        assertNotNull(finalStatus);
        assertEquals(FulfillmentTaskStatus.SUCCESS.name(), finalStatus.getStatus(), "任务最终应该成功");
        assertEquals(1, finalStatus.getRetryCount(), "重试次数应为 1 (首次失败 retryCount 0→1，重试成功不再递增)");
    }

    // ==================== 手动补发 ====================

    @Test
    @DisplayName("手动补发 - 正常成功")
    void manualFulfill_success() {
        ManualFulfillRequest request = new ManualFulfillRequest();
        request.setIdempotentKey("MANUAL-IDEM-001");
        request.setBizOrderNo("ORDER-006");
        request.setBizUserRef("USER-006");
        request.setBenefitTypeCode("MEMBERSHIP");
        request.setBenefitConfigSnapshot("{\"days\":30}");
        request.setReason("用户投诉未到账");

        FulfillmentResponse response = fulfillmentService.manualFulfill(request);

        assertTrue(response.isSuccess());
        assertEquals("SUCCESS", response.getStatus());
    }

    @Test
    @DisplayName("手动补发 - 幂等重放")
    void manualFulfill_idempotentReplay() {
        ManualFulfillRequest request = new ManualFulfillRequest();
        request.setIdempotentKey("MANUAL-IDEM-002");
        request.setBizOrderNo("ORDER-007");
        request.setBizUserRef("USER-007");
        request.setBenefitTypeCode("API_QUOTA");
        request.setBenefitConfigSnapshot("{\"quota\":100}");

        FulfillmentResponse first = fulfillmentService.manualFulfill(request);
        assertTrue(first.isSuccess());

        // 同一幂等键再次调用
        FulfillmentResponse second = fulfillmentService.manualFulfill(request);
        assertEquals(first.getTaskNo(), second.getTaskNo());
    }

    @Test
    @DisplayName("手动补发 - idempotentKey 为空时抛出异常")
    void manualFulfill_idempotentKeyBlank_throwsException() {
        ManualFulfillRequest request = new ManualFulfillRequest();
        // 不设置 idempotentKey（null）
        request.setBizOrderNo("ORDER-BLANK-001");
        request.setBizUserRef("USER-BLANK-001");
        request.setBenefitTypeCode("MEMBERSHIP");
        request.setBenefitConfigSnapshot("{\"days\":30}");
        request.setReason("用户投诉未到账");

        BizException ex = assertThrows(BizException.class, () ->
                fulfillmentService.manualFulfill(request));
        assertEquals("INVALID_PARAMETER", ex.getCode());
        assertTrue(ex.getMessage().contains("idempotentKey"), "错误信息应包含 idempotentKey");
    }

    @Test
    @DisplayName("手动补发 - idempotentKey 为空白字符串时抛出异常")
    void manualFulfill_idempotentKeyWhitespace_throwsException() {
        ManualFulfillRequest request = new ManualFulfillRequest();
        request.setIdempotentKey("   "); // 仅空格
        request.setBizOrderNo("ORDER-BLANK-002");
        request.setBizUserRef("USER-BLANK-002");
        request.setBenefitTypeCode("MEMBERSHIP");
        request.setBenefitConfigSnapshot("{\"days\":30}");

        BizException ex = assertThrows(BizException.class, () ->
                fulfillmentService.manualFulfill(request));
        assertEquals("INVALID_PARAMETER", ex.getCode());
        assertTrue(ex.getMessage().contains("idempotentKey"), "错误信息应包含 idempotentKey");
    }

    // ==================== 幂等键带空格标准化 ====================

    @Test
    @DisplayName("支付确认 - 显式 idempotentKey 带空格时自动 trim 后幂等重放一致")
    void confirmPaidAndFulfill_idempotentKeyWithSpaces_trimmedReplay() {
        // 第一次：传带空格的幂等键
        ConfirmPaymentRequest request = buildConfirmRequest("ORDER-TRIM-001", "PAY-TRIM-001", "MEMBERSHIP");
        request.setIdempotentKey("  TRIM-KEY-001  ");

        FulfillmentResponse first = fulfillmentService.confirmPaidAndFulfill(request);
        assertTrue(first.isSuccess());

        // 第二次：传去掉空格的同一幂等键
        ConfirmPaymentRequest request2 = buildConfirmRequest("ORDER-TRIM-001", "PAY-TRIM-001", "MEMBERSHIP");
        request2.setIdempotentKey("TRIM-KEY-001");

        FulfillmentResponse second = fulfillmentService.confirmPaidAndFulfill(request2);
        assertNotNull(second);
        assertEquals(first.getTaskNo(), second.getTaskNo(), "trim 前后应是同一幂等键，返回同一任务");
        assertEquals("idempotent replay", second.getMessage());
    }

    @Test
    @DisplayName("手动补发 - 显式 idempotentKey 带空格时自动 trim 后幂等重放一致")
    void manualFulfill_idempotentKeyWithSpaces_trimmedReplay() {
        // 第一次：传带空格的幂等键
        ManualFulfillRequest request = new ManualFulfillRequest();
        request.setIdempotentKey("  MANUAL-TRIM-KEY  ");
        request.setBizOrderNo("ORDER-TRIM-002");
        request.setBizUserRef("USER-TRIM-002");
        request.setBenefitTypeCode("MEMBERSHIP");
        request.setBenefitConfigSnapshot("{\"days\":30}");

        FulfillmentResponse first = fulfillmentService.manualFulfill(request);
        assertTrue(first.isSuccess());

        // 第二次：传去掉空格的同一幂等键
        ManualFulfillRequest request2 = new ManualFulfillRequest();
        request2.setIdempotentKey("MANUAL-TRIM-KEY");
        request2.setBizOrderNo("ORDER-TRIM-002");
        request2.setBizUserRef("USER-TRIM-002");
        request2.setBenefitTypeCode("MEMBERSHIP");
        request2.setBenefitConfigSnapshot("{\"days\":30}");

        FulfillmentResponse second = fulfillmentService.manualFulfill(request2);
        assertNotNull(second);
        assertEquals(first.getTaskNo(), second.getTaskNo(), "trim 前后应是同一幂等键，返回同一任务");
        assertEquals("idempotent replay", second.getMessage());
    }

    @Test
    @DisplayName("生命周期动作 - 显式 idempotentKey 带空格时自动 trim 后幂等重放一致")
    void executeLifecycleAction_idempotentKeyWithSpaces_trimmedReplay() {
        // 第一次：带空格的幂等键
        ExecuteLifecycleActionRequest request = buildLifecycleRequest(
                "  LC-TRIM-KEY  ", "ORDER-TRIM-003", "MEMBERSHIP",
                LifecycleActionCodes.GRANT, "{\"days\":30}");
        FulfillmentResponse first = fulfillmentService.executeLifecycleAction(request);
        assertTrue(first.isSuccess());

        // 第二次：去掉空格的同一幂等键
        ExecuteLifecycleActionRequest request2 = buildLifecycleRequest(
                "LC-TRIM-KEY", "ORDER-TRIM-003", "MEMBERSHIP",
                LifecycleActionCodes.GRANT, "{\"days\":30}");
        FulfillmentResponse second = fulfillmentService.executeLifecycleAction(request2);
        assertNotNull(second);
        assertEquals(first.getTaskNo(), second.getTaskNo(), "trim 前后应是同一幂等键，返回同一任务");
        assertEquals("idempotent replay", second.getMessage());
    }

    // ==================== 查询 ====================

    @Test
    @DisplayName("按订单号查询 - 存在")
    void queryByOrderNo_found() {
        ConfirmPaymentRequest request = buildConfirmRequest("ORDER-008", "PAY-008", "MEMBERSHIP");
        fulfillmentService.confirmPaidAndFulfill(request);

        FulfillmentStatusResponse status = fulfillmentService.queryByOrderNo("ORDER-008");
        assertNotNull(status);
        assertEquals("ORDER-008", status.getBizOrderNo());
        assertEquals("SUCCESS", status.getStatus());
    }

    @Test
    @DisplayName("按订单号查询 - 不存在返回 null")
    void queryByOrderNo_notFound() {
        FulfillmentStatusResponse status = fulfillmentService.queryByOrderNo("NON-EXISTENT");
        assertNull(status, "不存在的订单应返回 null");
    }

    @Test
    @DisplayName("按任务号查询详情 - 包含执行日志")
    void queryByTaskNo_withLogs() {
        ConfirmPaymentRequest request = buildConfirmRequest("ORDER-009", "PAY-009", "MEMBERSHIP");
        FulfillmentResponse response = fulfillmentService.confirmPaidAndFulfill(request);

        FulfillmentDetailResponse detail = fulfillmentService.queryByTaskNo(response.getTaskNo());

        assertNotNull(detail);
        assertEquals(response.getTaskNo(), detail.getTaskNo());
        assertNotNull(detail.getLogs(), "应包含执行日志");
        assertFalse(detail.getLogs().isEmpty(), "至少有一条执行记录");
        assertEquals("SUCCESS", detail.getLogs().get(0).getStatus());
        assertNotNull(detail.getLogs().get(0).getDurationMs());
    }

    @Test
    @DisplayName("按任务号查询 - 不存在抛异常")
    void queryByTaskNo_notFound() {
        BizException ex = assertThrows(BizException.class, () ->
                fulfillmentService.queryByTaskNo("FAKE-TASK"));
        assertEquals("TASK_NOT_FOUND", ex.getCode());
    }

    // ==================== 生命周期动作 - GRANT ====================

    @Test
    @DisplayName("生命周期动作 - GRANT 成功（通用 Handler 处理）")
    void executeLifecycleAction_grant_success() {
        ExecuteLifecycleActionRequest request = buildLifecycleRequest(
                "LC-GRANT-001", "ORDER-LC-001", "MEMBERSHIP",
                LifecycleActionCodes.GRANT, "{\"days\":30}");
        request.setReason("运营主动授予会员");

        FulfillmentResponse response = fulfillmentService.executeLifecycleAction(request);

        assertNotNull(response);
        assertTrue(response.isSuccess(), "GRANT 动作应该成功");
        assertEquals("SUCCESS", response.getStatus());
        assertNotNull(response.getTaskNo());

        // 验证 actionCode 正确持久化
        FulfillmentStatusResponse status = fulfillmentService.queryByOrderNo("ORDER-LC-001");
        assertNotNull(status);
        assertEquals(LifecycleActionCodes.GRANT, status.getActionCode(), "actionCode 应为 GRANT");
        assertEquals("MEMBERSHIP", status.getBenefitTypeCode());
    }

    // ==================== 生命周期动作 - REVOKE ====================

    @Test
    @DisplayName("生命周期动作 - REVOKE 成功（动作专用 Handler 处理）")
    void executeLifecycleAction_revoke_success() {
        ExecuteLifecycleActionRequest request = buildLifecycleRequest(
                "LC-REVOKE-001", "ORDER-LC-002", "MEMBERSHIP",
                LifecycleActionCodes.REVOKE, "{\"reason\":\"退款\"}");
        request.setReason("用户申请退款，撤销会员权益");

        FulfillmentResponse response = fulfillmentService.executeLifecycleAction(request);

        assertNotNull(response);
        assertTrue(response.isSuccess(), "REVOKE 动作应该成功");
        assertEquals("SUCCESS", response.getStatus());

        // 验证 actionCode
        FulfillmentStatusResponse status = fulfillmentService.queryByOrderNo("ORDER-LC-002");
        assertNotNull(status);
        assertEquals(LifecycleActionCodes.REVOKE, status.getActionCode(), "actionCode 应为 REVOKE");

        // 验证日志中也包含 actionCode
        FulfillmentDetailResponse detail = fulfillmentService.queryByTaskNo(response.getTaskNo());
        assertNotNull(detail.getLogs());
        assertFalse(detail.getLogs().isEmpty());
        assertEquals(LifecycleActionCodes.REVOKE, detail.getLogs().get(0).getActionCode(),
                "日志的 actionCode 应为 REVOKE");
    }

    @Test
    @DisplayName("生命周期动作 - REVOKE 使用专用 Handler 而非通用 Handler")
    void executeLifecycleAction_revoke_usesSpecificHandler() {
        // 先发放一个 GRANT，再 REVOKE
        ExecuteLifecycleActionRequest grantReq = buildLifecycleRequest(
                "LC-SPEC-GRANT", "ORDER-LC-SPEC", "MEMBERSHIP",
                LifecycleActionCodes.GRANT, "{\"days\":30}");
        FulfillmentResponse grantResp = fulfillmentService.executeLifecycleAction(grantReq);
        assertTrue(grantResp.isSuccess());

        // REVOKE 相同权益
        ExecuteLifecycleActionRequest revokeReq = buildLifecycleRequest(
                "LC-SPEC-REVOKE", "ORDER-LC-SPEC-R", "MEMBERSHIP",
                LifecycleActionCodes.REVOKE, "{\"reason\":\"退款\"}");
        FulfillmentResponse revokeResp = fulfillmentService.executeLifecycleAction(revokeReq);
        assertTrue(revokeResp.isSuccess());

        // 验证 REVOKE 使用的是 TestMembershipRevokeHandler
        FulfillmentDetailResponse revokeDetail = fulfillmentService.queryByTaskNo(revokeResp.getTaskNo());
        assertEquals("TestMembershipRevokeHandler", revokeDetail.getLogs().get(0).getHandlerName(),
                "REVOKE 应匹配到动作专用 Handler");

        // 验证 GRANT 使用的是 TestMembershipHandler
        FulfillmentDetailResponse grantDetail = fulfillmentService.queryByTaskNo(grantResp.getTaskNo());
        assertEquals("TestMembershipHandler", grantDetail.getLogs().get(0).getHandlerName(),
                "GRANT 应匹配到通用 Handler");
    }

    // ==================== 生命周期动作 - RENEW ====================

    @Test
    @DisplayName("生命周期动作 - RENEW 匹配到注解式专用 Handler")
    void executeLifecycleAction_renew_matchesAnnotatedHandler() {
        // MEMBERSHIP 的 RENEW 有注解式专用 Handler TestAnnotatedRenewHandler
        ExecuteLifecycleActionRequest request = buildLifecycleRequest(
                "LC-RENEW-001", "ORDER-LC-003", "MEMBERSHIP",
                LifecycleActionCodes.RENEW, "{\"months\":1}");
        request.setReason("会员续费");

        FulfillmentResponse response = fulfillmentService.executeLifecycleAction(request);

        assertNotNull(response);
        assertTrue(response.isSuccess(), "RENEW 应匹配到注解式专用 Handler 并成功");
        assertEquals("SUCCESS", response.getStatus());

        // 验证 actionCode
        FulfillmentStatusResponse status = fulfillmentService.queryByOrderNo("ORDER-LC-003");
        assertEquals(LifecycleActionCodes.RENEW, status.getActionCode());

        // 验证使用的是注解式 Handler
        FulfillmentDetailResponse detail = fulfillmentService.queryByTaskNo(response.getTaskNo());
        assertEquals("TestAnnotatedRenewHandler", detail.getLogs().get(0).getHandlerName(),
                "RENEW 应匹配到注解式专用 Handler");
    }

    @Test
    @DisplayName("生命周期动作 - 同一权益类型三种动作分别匹配正确的 Handler")
    void executeLifecycleAction_threeActionsMatchCorrectHandlers() {
        // GRANT → 通用 TestMembershipHandler
        ExecuteLifecycleActionRequest grantReq = buildLifecycleRequest(
                "LC-3ACT-GRANT", "ORDER-3ACT-G", "MEMBERSHIP",
                LifecycleActionCodes.GRANT, "{\"days\":30}");
        FulfillmentResponse grantResp = fulfillmentService.executeLifecycleAction(grantReq);
        assertTrue(grantResp.isSuccess());
        FulfillmentDetailResponse grantDetail = fulfillmentService.queryByTaskNo(grantResp.getTaskNo());
        assertEquals("TestMembershipHandler", grantDetail.getLogs().get(0).getHandlerName(),
                "GRANT 应匹配通用 Handler");

        // REVOKE → 专用 TestMembershipRevokeHandler
        ExecuteLifecycleActionRequest revokeReq = buildLifecycleRequest(
                "LC-3ACT-REVOKE", "ORDER-3ACT-R", "MEMBERSHIP",
                LifecycleActionCodes.REVOKE, "{\"reason\":\"退款\"}");
        FulfillmentResponse revokeResp = fulfillmentService.executeLifecycleAction(revokeReq);
        assertTrue(revokeResp.isSuccess());
        FulfillmentDetailResponse revokeDetail = fulfillmentService.queryByTaskNo(revokeResp.getTaskNo());
        assertEquals("TestMembershipRevokeHandler", revokeDetail.getLogs().get(0).getHandlerName(),
                "REVOKE 应匹配专用 Handler");

        // RENEW → 注解式专用 TestAnnotatedRenewHandler
        ExecuteLifecycleActionRequest renewReq = buildLifecycleRequest(
                "LC-3ACT-RENEW", "ORDER-3ACT-N", "MEMBERSHIP",
                LifecycleActionCodes.RENEW, "{\"months\":1}");
        FulfillmentResponse renewResp = fulfillmentService.executeLifecycleAction(renewReq);
        assertTrue(renewResp.isSuccess());
        FulfillmentDetailResponse renewDetail = fulfillmentService.queryByTaskNo(renewResp.getTaskNo());
        assertEquals("TestAnnotatedRenewHandler", renewDetail.getLogs().get(0).getHandlerName(),
                "RENEW 应匹配到注解式专用 Handler");
    }

    // ==================== 生命周期动作 - Handler 未找到 ====================

    @Test
    @DisplayName("生命周期动作 - 无匹配 Handler 失败")
    void executeLifecycleAction_noHandler() {
        // CUSTOM 类型没有注册 Handler
        ExecuteLifecycleActionRequest request = buildLifecycleRequest(
                "LC-NOOP-001", "ORDER-LC-004", "CUSTOM",
                LifecycleActionCodes.REVOKE, "{}");

        FulfillmentResponse response = fulfillmentService.executeLifecycleAction(request);

        assertNotNull(response);
        assertFalse(response.isSuccess());
        assertEquals("FAILED", response.getStatus());
        assertEquals("HANDLER_NOT_FOUND", response.getErrorCode());
    }

    // ==================== 生命周期动作 - 幂等 ====================

    @Test
    @DisplayName("生命周期动作 - 显式幂等键重放")
    void executeLifecycleAction_idempotentReplay_explicitKey() {
        ExecuteLifecycleActionRequest request = buildLifecycleRequest(
                "LC-IDEM-001", "ORDER-LC-005", "MEMBERSHIP",
                LifecycleActionCodes.REVOKE, "{\"reason\":\"退款\"}");

        // 第一次调用
        FulfillmentResponse first = fulfillmentService.executeLifecycleAction(request);
        assertTrue(first.isSuccess());

        // 第二次调用（同一幂等键）
        FulfillmentResponse second = fulfillmentService.executeLifecycleAction(request);
        assertNotNull(second);
        assertEquals(first.getTaskNo(), second.getTaskNo(), "幂等重放应返回同一任务编号");
        assertEquals("idempotent replay", second.getMessage());
    }

    @Test
    @DisplayName("生命周期动作 - 自动幂等键（不传 idempotentKey）")
    void executeLifecycleAction_autoIdempotentKey() {
        // 不传 idempotentKey，SDK 自动按 actionCode + bizOrderNo 生成
        ExecuteLifecycleActionRequest request = new ExecuteLifecycleActionRequest();
        request.setBizOrderNo("ORDER-LC-AUTO-001");
        request.setBizUserRef("USER-LC-AUTO-001");
        request.setBenefitTypeCode("MEMBERSHIP");
        request.setActionCode(LifecycleActionCodes.GRANT);
        request.setBenefitConfigSnapshot("{\"days\":30}");

        // 第一次调用
        FulfillmentResponse first = fulfillmentService.executeLifecycleAction(request);
        assertTrue(first.isSuccess());

        // 第二次调用（同样不传 idempotentKey，应自动生成相同的 key）
        FulfillmentResponse second = fulfillmentService.executeLifecycleAction(request);
        assertNotNull(second);
        assertEquals(first.getTaskNo(), second.getTaskNo(), "自动幂等键重放应返回同一任务编号");
        assertEquals("idempotent replay", second.getMessage());
    }

    @Test
    @DisplayName("生命周期动作 - 同一订单不同动作不互相冲突")
    void executeLifecycleAction_differentActionsDoNotConflict() {
        String orderNo = "ORDER-LC-CONFLICT-001";

        // GRANT
        ExecuteLifecycleActionRequest grantReq = new ExecuteLifecycleActionRequest();
        grantReq.setBizOrderNo(orderNo);
        grantReq.setBizUserRef("USER-CONFLICT");
        grantReq.setBenefitTypeCode("MEMBERSHIP");
        grantReq.setActionCode(LifecycleActionCodes.GRANT);
        grantReq.setBenefitConfigSnapshot("{\"days\":30}");

        FulfillmentResponse grantResp = fulfillmentService.executeLifecycleAction(grantReq);
        assertTrue(grantResp.isSuccess(), "GRANT 应成功");

        // REVOKE（同一订单号，不同动作 → 不同幂等键 → 应创建新任务）
        ExecuteLifecycleActionRequest revokeReq = new ExecuteLifecycleActionRequest();
        revokeReq.setBizOrderNo(orderNo);
        revokeReq.setBizUserRef("USER-CONFLICT");
        revokeReq.setBenefitTypeCode("MEMBERSHIP");
        revokeReq.setActionCode(LifecycleActionCodes.REVOKE);
        revokeReq.setBenefitConfigSnapshot("{\"reason\":\"退款\"}");

        FulfillmentResponse revokeResp = fulfillmentService.executeLifecycleAction(revokeReq);
        assertTrue(revokeResp.isSuccess(), "REVOKE 应成功");
        assertNotEquals(grantResp.getTaskNo(), revokeResp.getTaskNo(),
                "同一订单的不同动作应创建不同的任务（幂等键不同）");
    }

    // ==================== 生命周期动作 - actionCode 归一化 ====================

    @Test
    @DisplayName("生命周期动作 - actionCode 自动归一化（trim + uppercase）")
    void executeLifecycleAction_actionCodeNormalization() {
        // 测试小写、带空格等格式，SDK 应自动归一化为大写
        ExecuteLifecycleActionRequest request = new ExecuteLifecycleActionRequest();
        request.setBizOrderNo("ORDER-NORM-001");
        request.setBizUserRef("USER-NORM-001");
        request.setBenefitTypeCode("MEMBERSHIP");
        request.setActionCode("  revoke  "); // 小写 + 前后空格
        request.setBenefitConfigSnapshot("{\"reason\":\"退款\"}");

        FulfillmentResponse response = fulfillmentService.executeLifecycleAction(request);
        assertTrue(response.isSuccess(), "归一化后的 actionCode 应能正常处理");

        // 验证数据库中存储的是归一化后的值（大写、无空格）
        FulfillmentStatusResponse status = fulfillmentService.queryByOrderNo("ORDER-NORM-001");
        assertEquals("REVOKE", status.getActionCode(), "actionCode 应被归一化为大写 REVOKE");
    }

    @Test
    @DisplayName("生命周期动作 - actionCode 小写混合大小写归一化后匹配专用 Handler")
    void executeLifecycleAction_actionCodeMixedCase_matchesSpecificHandler() {
        // 传入 "Revoke"（混合大小写），应归一化为 "REVOKE" 后匹配 TestMembershipRevokeHandler
        ExecuteLifecycleActionRequest request = new ExecuteLifecycleActionRequest();
        request.setBizOrderNo("ORDER-CASE-001");
        request.setBizUserRef("USER-CASE-001");
        request.setBenefitTypeCode("MEMBERSHIP");
        request.setActionCode("Revoke"); // 混合大小写
        request.setBenefitConfigSnapshot("{\"reason\":\"退款\"}");

        FulfillmentResponse response = fulfillmentService.executeLifecycleAction(request);
        assertTrue(response.isSuccess(), "混合大小写 actionCode 归一化后应能正常处理");

        // 验证匹配到专用 Handler
        FulfillmentDetailResponse detail = fulfillmentService.queryByTaskNo(response.getTaskNo());
        assertEquals("TestMembershipRevokeHandler", detail.getLogs().get(0).getHandlerName(),
                "归一化后应匹配到 REVOKE 专用 Handler");
        assertEquals("REVOKE", detail.getActionCode(), "actionCode 应被归一化为大写");
    }

    @Test
    @DisplayName("生命周期动作 - actionCode 全小写 'grant' 归一化后正常处理")
    void executeLifecycleAction_actionCodeLowerCase_normalizedToGrant() {
        ExecuteLifecycleActionRequest request = new ExecuteLifecycleActionRequest();
        request.setBizOrderNo("ORDER-CASE-002");
        request.setBizUserRef("USER-CASE-002");
        request.setBenefitTypeCode("MEMBERSHIP");
        request.setActionCode("grant"); // 全小写
        request.setBenefitConfigSnapshot("{\"days\":30}");

        FulfillmentResponse response = fulfillmentService.executeLifecycleAction(request);
        assertTrue(response.isSuccess());

        FulfillmentStatusResponse status = fulfillmentService.queryByOrderNo("ORDER-CASE-002");
        assertEquals("GRANT", status.getActionCode(), "actionCode 应被归一化为大写 GRANT");
    }

    @Test
    @DisplayName("生命周期动作 - actionCode 为空时抛出异常")
    void executeLifecycleAction_actionCodeBlank_throwsException() {
        ExecuteLifecycleActionRequest request = new ExecuteLifecycleActionRequest();
        request.setBizOrderNo("ORDER-BLANK-001");
        request.setBizUserRef("USER-BLANK-001");
        request.setBenefitTypeCode("MEMBERSHIP");
        request.setActionCode("   "); // 仅空格
        request.setBenefitConfigSnapshot("{\"days\":30}");

        BizException ex = assertThrows(BizException.class, () ->
                fulfillmentService.executeLifecycleAction(request));
        assertEquals("INVALID_PARAMETER", ex.getCode());
        assertTrue(ex.getMessage().contains("actionCode"), "错误信息应包含 actionCode");
    }

    // ==================== 生命周期动作 - API_QUOTA GRANT ====================

    @Test
    @DisplayName("生命周期动作 - API_QUOTA GRANT 成功")
    void executeLifecycleAction_apiQuota_grant() {
        ExecuteLifecycleActionRequest request = buildLifecycleRequest(
                "LC-AQ-001", "ORDER-LC-006", "API_QUOTA",
                LifecycleActionCodes.GRANT, "{\"quota\":500}");

        FulfillmentResponse response = fulfillmentService.executeLifecycleAction(request);

        assertTrue(response.isSuccess());
        assertEquals("SUCCESS", response.getStatus());

        FulfillmentStatusResponse status = fulfillmentService.queryByOrderNo("ORDER-LC-006");
        assertEquals(LifecycleActionCodes.GRANT, status.getActionCode());
        assertEquals("API_QUOTA", status.getBenefitTypeCode());
    }

    // ==================== 按订单号 + 动作查询 ====================

    @Test
    @DisplayName("queryByOrderNoAndAction - 精确查询 GRANT 动作状态")
    void queryByOrderNoAndAction_grant() {
        String orderNo = "ORDER-QA-001";
        // 先执行 GRANT
        ExecuteLifecycleActionRequest grantReq = buildLifecycleRequest(
                "QA-GRANT-001", orderNo, "MEMBERSHIP",
                LifecycleActionCodes.GRANT, "{\"days\":30}");
        FulfillmentResponse grantResp = fulfillmentService.executeLifecycleAction(grantReq);
        assertTrue(grantResp.isSuccess());

        // 再执行 REVOKE（使该订单有多条任务）
        ExecuteLifecycleActionRequest revokeReq = buildLifecycleRequest(
                "QA-REVOKE-001", orderNo, "MEMBERSHIP",
                LifecycleActionCodes.REVOKE, "{\"reason\":\"退款\"}");
        FulfillmentResponse revokeResp = fulfillmentService.executeLifecycleAction(revokeReq);
        assertTrue(revokeResp.isSuccess());

        // queryByOrderNo 只返回最新任务（REVOKE）
        FulfillmentStatusResponse latest = fulfillmentService.queryByOrderNo(orderNo);
        assertEquals(LifecycleActionCodes.REVOKE, latest.getActionCode(),
                "queryByOrderNo 应返回最新的 REVOKE 任务");

        // queryByOrderNoAndAction 能精确查到 GRANT
        FulfillmentStatusResponse grantStatus = fulfillmentService.queryByOrderNoAndAction(orderNo, "GRANT");
        assertNotNull(grantStatus, "应能查到 GRANT 任务");
        assertEquals(LifecycleActionCodes.GRANT, grantStatus.getActionCode());
        assertEquals(grantResp.getTaskNo(), grantStatus.getTaskNo());
        assertEquals("SUCCESS", grantStatus.getStatus());

        // queryByOrderNoAndAction 也能精确查到 REVOKE
        FulfillmentStatusResponse revokeStatus = fulfillmentService.queryByOrderNoAndAction(orderNo, "REVOKE");
        assertNotNull(revokeStatus);
        assertEquals(LifecycleActionCodes.REVOKE, revokeStatus.getActionCode());
        assertEquals(revokeResp.getTaskNo(), revokeStatus.getTaskNo());
    }

    @Test
    @DisplayName("queryByOrderNoAndAction - actionCode 自动归一化（小写输入）")
    void queryByOrderNoAndAction_normalizedActionCode() {
        ExecuteLifecycleActionRequest req = buildLifecycleRequest(
                "QA-NORM-001", "ORDER-QA-002", "MEMBERSHIP",
                LifecycleActionCodes.GRANT, "{\"days\":30}");
        fulfillmentService.executeLifecycleAction(req);

        // 用小写 "grant" 查询，应自动归一化为 "GRANT"
        FulfillmentStatusResponse status = fulfillmentService.queryByOrderNoAndAction("ORDER-QA-002", "grant");
        assertNotNull(status, "小写 actionCode 应自动归一化后查到结果");
        assertEquals(LifecycleActionCodes.GRANT, status.getActionCode());
    }

    @Test
    @DisplayName("queryByOrderNoAndAction - 不存在返回 null")
    void queryByOrderNoAndAction_notFound() {
        FulfillmentStatusResponse status = fulfillmentService.queryByOrderNoAndAction("NON-EXISTENT", "GRANT");
        assertNull(status, "不存在的订单+动作应返回 null");
    }

    @Test
    @DisplayName("queryByOrderNoAndAction - actionCode 为空抛出异常")
    void queryByOrderNoAndAction_blankActionCode() {
        BizException ex = assertThrows(BizException.class, () ->
                fulfillmentService.queryByOrderNoAndAction("ORDER-QA-003", "  "));
        assertEquals("INVALID_PARAMETER", ex.getCode());
    }

    // ==================== 当前权益状态查询 ====================

    @Test
    @DisplayName("queryCurrentEntitlement - GRANT 后权益有效")
    void queryCurrentEntitlement_afterGrant() {
        String orderNo = "ORDER-ENT-001";
        ExecuteLifecycleActionRequest req = buildLifecycleRequest(
                "ENT-GRANT-001", orderNo, "MEMBERSHIP",
                LifecycleActionCodes.GRANT, "{\"days\":30}");
        fulfillmentService.executeLifecycleAction(req);

        EntitlementStatusResponse resp = fulfillmentService.queryCurrentEntitlement(orderNo, "MEMBERSHIP");

        assertNotNull(resp);
        assertEquals(orderNo, resp.getBizOrderNo());
        assertEquals("MEMBERSHIP", resp.getBenefitTypeCode());
        assertEquals(LifecycleActionCodes.GRANT, resp.getLastSuccessfulAction(),
                "最近成功动作应为 GRANT");
        assertNotNull(resp.getLastSuccessfulTaskNo());
        assertNotNull(resp.getLastSuccessfulTime());
        assertEquals(1, resp.getTotalTaskCount());
        assertEquals(1, resp.getSuccessCount());
        assertEquals(0, resp.getPendingCount());
        assertEquals(0, resp.getFailedCount());
    }

    @Test
    @DisplayName("queryCurrentEntitlement - GRANT 再 REVOKE 后权益撤销")
    void queryCurrentEntitlement_afterGrantThenRevoke() {
        String orderNo = "ORDER-ENT-002";
        // GRANT
        ExecuteLifecycleActionRequest grantReq = buildLifecycleRequest(
                "ENT-GR-002", orderNo, "MEMBERSHIP",
                LifecycleActionCodes.GRANT, "{\"days\":30}");
        fulfillmentService.executeLifecycleAction(grantReq);

        // REVOKE
        ExecuteLifecycleActionRequest revokeReq = buildLifecycleRequest(
                "ENT-RV-002", orderNo, "MEMBERSHIP",
                LifecycleActionCodes.REVOKE, "{\"reason\":\"退款\"}");
        fulfillmentService.executeLifecycleAction(revokeReq);

        EntitlementStatusResponse resp = fulfillmentService.queryCurrentEntitlement(orderNo, "MEMBERSHIP");

        assertNotNull(resp);
        assertEquals(LifecycleActionCodes.REVOKE, resp.getLastSuccessfulAction(),
                "GRANT→REVOKE 后，最近成功动作应为 REVOKE");
        assertEquals(2, resp.getTotalTaskCount());
        assertEquals(2, resp.getSuccessCount());
        assertEquals(0, resp.getPendingCount());
        // 最新任务也是 REVOKE
        assertEquals(LifecycleActionCodes.REVOKE, resp.getLatestActionCode());
        assertEquals("SUCCESS", resp.getLatestTaskStatus());
    }

    @Test
    @DisplayName("queryCurrentEntitlement - GRANT → REVOKE → RENEW 完整生命周期")
    void queryCurrentEntitlement_fullLifecycle() {
        String orderNo = "ORDER-ENT-003";
        // GRANT
        fulfillmentService.executeLifecycleAction(buildLifecycleRequest(
                "ENT-GR-003", orderNo, "MEMBERSHIP",
                LifecycleActionCodes.GRANT, "{\"days\":30}"));
        // REVOKE
        fulfillmentService.executeLifecycleAction(buildLifecycleRequest(
                "ENT-RV-003", orderNo, "MEMBERSHIP",
                LifecycleActionCodes.REVOKE, "{\"reason\":\"退款\"}"));
        // RENEW
        fulfillmentService.executeLifecycleAction(buildLifecycleRequest(
                "ENT-RN-003", orderNo, "MEMBERSHIP",
                LifecycleActionCodes.RENEW, "{\"months\":1}"));

        EntitlementStatusResponse resp = fulfillmentService.queryCurrentEntitlement(orderNo, "MEMBERSHIP");

        assertNotNull(resp);
        assertEquals(LifecycleActionCodes.RENEW, resp.getLastSuccessfulAction(),
                "完整生命周期后，最近成功动作应为 RENEW");
        assertEquals(3, resp.getTotalTaskCount());
        assertEquals(3, resp.getSuccessCount());
        assertEquals(LifecycleActionCodes.RENEW, resp.getLatestActionCode());
    }

    @Test
    @DisplayName("queryCurrentEntitlement - 不同权益类型互不干扰")
    void queryCurrentEntitlement_differentBenefitTypesIndependent() {
        String orderNo = "ORDER-ENT-004";
        // MEMBERSHIP GRANT
        fulfillmentService.executeLifecycleAction(buildLifecycleRequest(
                "ENT-MEM-004", orderNo, "MEMBERSHIP",
                LifecycleActionCodes.GRANT, "{\"days\":30}"));
        // API_QUOTA GRANT（同一订单，不同权益类型）
        fulfillmentService.executeLifecycleAction(buildLifecycleRequest(
                "ENT-AQ-004", orderNo, "API_QUOTA",
                LifecycleActionCodes.GRANT, "{\"quota\":500}"));

        // 查 MEMBERSHIP
        EntitlementStatusResponse membershipResp = fulfillmentService.queryCurrentEntitlement(orderNo, "MEMBERSHIP");
        assertNotNull(membershipResp);
        assertEquals("MEMBERSHIP", membershipResp.getBenefitTypeCode());
        assertEquals(1, membershipResp.getTotalTaskCount(), "MEMBERSHIP 应只有 1 条任务");

        // 查 API_QUOTA
        EntitlementStatusResponse quotaResp = fulfillmentService.queryCurrentEntitlement(orderNo, "API_QUOTA");
        assertNotNull(quotaResp);
        assertEquals("API_QUOTA", quotaResp.getBenefitTypeCode());
        assertEquals(1, quotaResp.getTotalTaskCount(), "API_QUOTA 应只有 1 条任务");
    }

    @Test
    @DisplayName("queryCurrentEntitlement - 有失败任务时也能正确聚合")
    void queryCurrentEntitlement_withFailedTask() {
        String orderNo = "ORDER-ENT-005";
        // 先发放一个没有 Handler 的权益类型（会失败）
        ExecuteLifecycleActionRequest failReq = buildLifecycleRequest(
                "ENT-FAIL-005", orderNo, "CUSTOM",
                LifecycleActionCodes.GRANT, "{\"data\":\"test\"}");
        FulfillmentResponse failResp = fulfillmentService.executeLifecycleAction(failReq);
        assertFalse(failResp.isSuccess());

        EntitlementStatusResponse resp = fulfillmentService.queryCurrentEntitlement(orderNo, "CUSTOM");
        assertNotNull(resp);
        assertNull(resp.getLastSuccessfulAction(), "没有成功任务时，lastSuccessfulAction 应为 null");
        assertEquals(1, resp.getTotalTaskCount());
        assertEquals(0, resp.getSuccessCount());
        assertEquals(1, resp.getFailedCount());
    }

    @Test
    @DisplayName("queryCurrentEntitlement - 不存在返回 null")
    void queryCurrentEntitlement_notFound() {
        EntitlementStatusResponse resp = fulfillmentService.queryCurrentEntitlement("NON-EXISTENT", "MEMBERSHIP");
        assertNull(resp, "不存在任何任务时应返回 null");
    }

    // ==================== @FulfillmentHandlerMapping 注解式注册 ====================

    @Test
    @DisplayName("注解式 Handler - RENEW 动作匹配注解式 Handler")
    void annotatedHandler_renew_matchesAnnotatedHandler() {
        ExecuteLifecycleActionRequest request = buildLifecycleRequest(
                "ANN-RENEW-001", "ORDER-ANN-001", "MEMBERSHIP",
                LifecycleActionCodes.RENEW, "{\"months\":1}");
        request.setReason("会员续费");

        FulfillmentResponse response = fulfillmentService.executeLifecycleAction(request);

        assertTrue(response.isSuccess(), "RENEW 应匹配注解式 Handler 并成功");
        assertEquals("SUCCESS", response.getStatus());

        // 验证匹配到注解式 Handler
        FulfillmentDetailResponse detail = fulfillmentService.queryByTaskNo(response.getTaskNo());
        assertEquals("TestAnnotatedRenewHandler", detail.getLogs().get(0).getHandlerName(),
                "RENEW 应匹配到 @FulfillmentHandlerMapping 注解式 Handler");
    }

    @Test
    @DisplayName("注解式 Handler - GRANT 仍然匹配通用编程式 Handler")
    void annotatedHandler_grantStillMatchesProgrammatic() {
        ExecuteLifecycleActionRequest request = buildLifecycleRequest(
                "ANN-GRANT-001", "ORDER-ANN-002", "MEMBERSHIP",
                LifecycleActionCodes.GRANT, "{\"days\":30}");

        FulfillmentResponse response = fulfillmentService.executeLifecycleAction(request);
        assertTrue(response.isSuccess());

        FulfillmentDetailResponse detail = fulfillmentService.queryByTaskNo(response.getTaskNo());
        assertEquals("TestMembershipHandler", detail.getLogs().get(0).getHandlerName(),
                "GRANT 应仍然匹配通用编程式 Handler");
    }

    @Test
    @DisplayName("注解式 Handler - REVOKE 仍然匹配编程式动作专用 Handler")
    void annotatedHandler_revokeStillMatchesProgrammaticSpecific() {
        ExecuteLifecycleActionRequest request = buildLifecycleRequest(
                "ANN-REVOKE-001", "ORDER-ANN-003", "MEMBERSHIP",
                LifecycleActionCodes.REVOKE, "{\"reason\":\"退款\"}");

        FulfillmentResponse response = fulfillmentService.executeLifecycleAction(request);
        assertTrue(response.isSuccess());

        FulfillmentDetailResponse detail = fulfillmentService.queryByTaskNo(response.getTaskNo());
        assertEquals("TestMembershipRevokeHandler", detail.getLogs().get(0).getHandlerName(),
                "REVOKE 应仍然匹配编程式动作专用 Handler");
    }

    // ==================== PROCESSING 卡死自愈 ====================

    @Test
    @DisplayName("recoverStuckTasks - 无卡死任务时返回 0")
    void recoverStuckTasks_noStuckTasks() {
        int recovered = fulfillmentService.recoverStuckTasks();
        assertEquals(0, recovered, "无卡死任务时应返回 0");
    }

    @Test
    @DisplayName("recoverStuckTasks - 模拟卡死任务恢复为 RETRY_WAIT")
    void recoverStuckTasks_recoversStuckTask() {
        // 1. 创建一个正常的发放任务
        ConfirmPaymentRequest request = buildConfirmRequest("ORDER-STUCK-001", "PAY-STUCK-001", "MEMBERSHIP");
        FulfillmentResponse response = fulfillmentService.confirmPaidAndFulfill(request);
        assertTrue(response.isSuccess());

        // 2. 手动把任务状态改为 PROCESSING，并将 updated_at 改为很久以前（模拟卡死）
        jdbcTemplate.update(
                "UPDATE sdk_fulfillment_task SET status = 'PROCESSING', updated_at = DATEADD('MINUTE', -60, CURRENT_TIMESTAMP) WHERE task_no = ?",
                response.getTaskNo());

        // 3. 执行卡死恢复
        int recovered = fulfillmentService.recoverStuckTasks();
        assertEquals(1, recovered, "应恢复 1 条卡死任务");

        // 4. 验证任务状态变为 RETRY_WAIT
        FulfillmentStatusResponse status = fulfillmentService.queryByOrderNo("ORDER-STUCK-001");
        assertEquals("RETRY_WAIT", status.getStatus(), "恢复后状态应为 RETRY_WAIT");
    }

    // ==================== next_retry_at 退避机制 ====================

    @Test
    @DisplayName("退避机制 - 失败任务设置 nextRetryAt")
    void retryBackoff_failedTaskSetsNextRetryAt() {
        // FILE_ACCESS handler 第一次总是失败
        ConfirmPaymentRequest request = buildConfirmRequest("ORDER-BACKOFF-001", "PAY-BACKOFF-001", "FILE_ACCESS");
        FulfillmentResponse response = fulfillmentService.confirmPaidAndFulfill(request);
        assertFalse(response.isSuccess());
        assertEquals("RETRY_WAIT", response.getStatus());

        // 验证数据库中 next_retry_at 不为空
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sdk_fulfillment_task WHERE task_no = ? AND next_retry_at IS NOT NULL",
                Integer.class, response.getTaskNo());
        assertEquals(1, count, "失败任务应设置 next_retry_at");
    }

    // ==================== 幂等并发安全测试 ====================

    @Test
    @DisplayName("幂等：相同请求重复调用只创建一条任务（消除 LOCKED 空窗期验证）")
    void idempotent_duplicateRequest_onlyOneTask() {
        ConfirmPaymentRequest req = buildConfirmRequest("ORDER-IDP-001", "PAY-IDP-001", "MEMBERSHIP");

        // 第一次调用：创建任务
        FulfillmentResponse first = fulfillmentService.confirmPaidAndFulfill(req);
        assertTrue(first.isSuccess());
        assertNotNull(first.getTaskNo());

        // 第二次调用（相同请求）：幂等重放
        ConfirmPaymentRequest req2 = buildConfirmRequest("ORDER-IDP-001", "PAY-IDP-001", "MEMBERSHIP");
        FulfillmentResponse second = fulfillmentService.confirmPaidAndFulfill(req2);
        assertNotNull(second.getTaskNo());
        assertEquals(first.getTaskNo(), second.getTaskNo(), "幂等重放应返回同一个 taskNo");
        assertEquals("idempotent replay", second.getMessage());
    }

    @Test
    @DisplayName("幂等：manualFulfill 相同 key 只创建一条任务")
    void idempotent_manualFulfill_duplicateKey() {
        ManualFulfillRequest req = new ManualFulfillRequest();
        req.setIdempotentKey("MANUAL-IDP-001");
        req.setBizOrderNo("ORDER-IDP-002");
        req.setBizUserRef("USER-IDP-002");
        req.setBenefitTypeCode("MEMBERSHIP");
        req.setBenefitConfigSnapshot("{\"days\":30}");
        req.setReason("测试幂等");

        FulfillmentResponse first = fulfillmentService.manualFulfill(req);
        assertTrue(first.isSuccess());

        // 重复请求
        ManualFulfillRequest req2 = new ManualFulfillRequest();
        req2.setIdempotentKey("MANUAL-IDP-001");
        req2.setBizOrderNo("ORDER-IDP-002");
        req2.setBizUserRef("USER-IDP-002");
        req2.setBenefitTypeCode("MEMBERSHIP");
        req2.setBenefitConfigSnapshot("{\"days\":30}");
        req2.setReason("测试幂等");

        FulfillmentResponse second = fulfillmentService.manualFulfill(req2);
        assertEquals(first.getTaskNo(), second.getTaskNo(), "manualFulfill 幂等重放应返回同一个 taskNo");
    }

    @Test
    @DisplayName("幂等：lifecycle 相同 key 只创建一条任务")
    void idempotent_lifecycle_duplicateKey() {
        ExecuteLifecycleActionRequest req = buildLifecycleRequest(
                "LC-IDP-001", "ORDER-IDP-003", "MEMBERSHIP",
                LifecycleActionCodes.GRANT, "{\"days\":30}");

        FulfillmentResponse first = fulfillmentService.executeLifecycleAction(req);
        assertTrue(first.isSuccess());

        // 重复请求
        ExecuteLifecycleActionRequest req2 = buildLifecycleRequest(
                "LC-IDP-001", "ORDER-IDP-003", "MEMBERSHIP",
                LifecycleActionCodes.GRANT, "{\"days\":30}");
        FulfillmentResponse second = fulfillmentService.executeLifecycleAction(req2);
        assertEquals(first.getTaskNo(), second.getTaskNo(), "lifecycle 幂等重放应返回同一个 taskNo");
    }

    @Test
    @DisplayName("幂等：不同 actionCode 应生成不同任务（不互相冲突）")
    void idempotent_differentActions_independentTasks() {
        String orderNo = "ORDER-IDP-004";
        // GRANT
        ExecuteLifecycleActionRequest grantReq = buildLifecycleRequest(
                null, orderNo, "MEMBERSHIP",
                LifecycleActionCodes.GRANT, "{\"days\":30}");
        FulfillmentResponse grantResp = fulfillmentService.executeLifecycleAction(grantReq);
        assertTrue(grantResp.isSuccess());

        // REVOKE（同一订单，不同 actionCode → 不同幂等键 → 不同任务）
        ExecuteLifecycleActionRequest revokeReq = buildLifecycleRequest(
                null, orderNo, "MEMBERSHIP",
                LifecycleActionCodes.REVOKE, "{\"reason\":\"退款\"}");
        FulfillmentResponse revokeResp = fulfillmentService.executeLifecycleAction(revokeReq);
        assertTrue(revokeResp.isSuccess());

        assertNotEquals(grantResp.getTaskNo(), revokeResp.getTaskNo(),
                "不同 actionCode 应产生不同的任务");
    }

    @Test
    @DisplayName("幂等：幂等记录中 taskNo 正确绑定，重放通过 taskNo 查找")
    void idempotent_replayUsesTaskNo() {
        // 创建第一个请求并获取 taskNo
        ConfirmPaymentRequest req = buildConfirmRequest("ORDER-IDP-005", "PAY-IDP-005", "MEMBERSHIP");
        FulfillmentResponse firstResp = fulfillmentService.confirmPaidAndFulfill(req);
        assertTrue(firstResp.isSuccess());
        String expectedTaskNo = firstResp.getTaskNo();

        // 验证通过 taskNo 可以查到完整任务
        FulfillmentDetailResponse detail = fulfillmentService.queryByTaskNo(expectedTaskNo);
        assertNotNull(detail);
        assertEquals("SUCCESS", detail.getStatus());

        // 再次请求应正确重放到同一 taskNo
        ConfirmPaymentRequest req2 = buildConfirmRequest("ORDER-IDP-005", "PAY-IDP-005", "MEMBERSHIP");
        FulfillmentResponse replayResp = fulfillmentService.confirmPaidAndFulfill(req2);
        assertEquals(expectedTaskNo, replayResp.getTaskNo());
    }

    // ==================== 工具方法 ====================

    private ConfirmPaymentRequest buildConfirmRequest(String orderNo, String paymentNo, String benefitTypeCode) {
        ConfirmPaymentRequest req = new ConfirmPaymentRequest();
        req.setBizOrderNo(orderNo);
        req.setBizUserRef("USER-" + orderNo);
        req.setPaymentNo(paymentNo);
        req.setChannelCode("ALIPAY");
        req.setPaidAmount(new BigDecimal("9.90"));
        req.setBenefitTypeCode(benefitTypeCode);
        req.setBenefitConfigSnapshot("{\"type\":\"" + benefitTypeCode + "\",\"days\":30}");
        return req;
    }

    private ExecuteLifecycleActionRequest buildLifecycleRequest(
            String idempotentKey, String orderNo, String benefitTypeCode,
            String actionCode, String configSnapshot) {
        ExecuteLifecycleActionRequest req = new ExecuteLifecycleActionRequest();
        req.setIdempotentKey(idempotentKey);
        req.setBizOrderNo(orderNo);
        req.setBizUserRef("USER-" + orderNo);
        req.setBenefitTypeCode(benefitTypeCode);
        req.setActionCode(actionCode);
        req.setBenefitConfigSnapshot(configSnapshot);
        return req;
    }
}
