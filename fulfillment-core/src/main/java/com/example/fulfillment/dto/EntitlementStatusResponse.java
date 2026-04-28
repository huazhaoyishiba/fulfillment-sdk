package com.example.fulfillment.dto;

import java.time.LocalDateTime;

/**
 * 当前权益状态响应
 * <p>
 * 聚合某个订单下某种权益类型的全部生命周期动作，
 * 提供"最近一次成功动作"和"当前任务概况"两类信息，
 * 供宿主应用据此判断权益的有效性。
 * <p>
 * 设计原则：SDK 只报告"发生了什么"，不替业务方定义"ACTIVE / REVOKED"语义。
 * 宿主应用可根据 {@link #getLastSuccessfulAction()} 自行推导业务含义。
 *
 * <pre>
 * 使用示例：
 *   EntitlementStatusResponse resp = service.queryCurrentEntitlement("ORDER-001", "MEMBERSHIP");
 *   if ("GRANT".equals(resp.getLastSuccessfulAction()) || "RENEW".equals(resp.getLastSuccessfulAction())) {
 *       // 权益有效
 *   } else if ("REVOKE".equals(resp.getLastSuccessfulAction())) {
 *       // 权益已撤销
 *   }
 * </pre>
 */
public class EntitlementStatusResponse {

    // ==================== 业务标识 ====================

    /** 业务订单号 */
    private String bizOrderNo;

    /** 用户业务标识 */
    private String bizUserRef;

    /** 权益类型编码 */
    private String benefitTypeCode;

    // ==================== 最近一次成功动作 ====================

    /**
     * 最近一次成功完成的动作编码（如 GRANT / REVOKE / RENEW）
     * <p>
     * 如果该订单下从未成功过任何动作，则为 null。
     */
    private String lastSuccessfulAction;

    /** 最近一次成功动作对应的任务编号 */
    private String lastSuccessfulTaskNo;

    /** 最近一次成功动作的完成时间 */
    private LocalDateTime lastSuccessfulTime;

    // ==================== 最新任务信息 ====================

    /** 最新创建的任务编号（无论成功还是失败） */
    private String latestTaskNo;

    /** 最新任务的动作编码 */
    private String latestActionCode;

    /** 最新任务的状态 */
    private String latestTaskStatus;

    /** 最新任务的创建时间 */
    private LocalDateTime latestTaskTime;

    // ==================== 汇总计数 ====================

    /** 该订单+权益类型下的任务总数 */
    private int totalTaskCount;

    /** 成功完成的任务数 */
    private int successCount;

    /** 进行中的任务数（PROCESSING + RETRY_WAIT） */
    private int pendingCount;

    /** 最终失败的任务数 */
    private int failedCount;

    // ==================== Getters & Setters ====================

    public String getBizOrderNo() { return bizOrderNo; }
    public void setBizOrderNo(String bizOrderNo) { this.bizOrderNo = bizOrderNo; }

    public String getBizUserRef() { return bizUserRef; }
    public void setBizUserRef(String bizUserRef) { this.bizUserRef = bizUserRef; }

    public String getBenefitTypeCode() { return benefitTypeCode; }
    public void setBenefitTypeCode(String benefitTypeCode) { this.benefitTypeCode = benefitTypeCode; }

    public String getLastSuccessfulAction() { return lastSuccessfulAction; }
    public void setLastSuccessfulAction(String lastSuccessfulAction) { this.lastSuccessfulAction = lastSuccessfulAction; }

    public String getLastSuccessfulTaskNo() { return lastSuccessfulTaskNo; }
    public void setLastSuccessfulTaskNo(String lastSuccessfulTaskNo) { this.lastSuccessfulTaskNo = lastSuccessfulTaskNo; }

    public LocalDateTime getLastSuccessfulTime() { return lastSuccessfulTime; }
    public void setLastSuccessfulTime(LocalDateTime lastSuccessfulTime) { this.lastSuccessfulTime = lastSuccessfulTime; }

    public String getLatestTaskNo() { return latestTaskNo; }
    public void setLatestTaskNo(String latestTaskNo) { this.latestTaskNo = latestTaskNo; }

    public String getLatestActionCode() { return latestActionCode; }
    public void setLatestActionCode(String latestActionCode) { this.latestActionCode = latestActionCode; }

    public String getLatestTaskStatus() { return latestTaskStatus; }
    public void setLatestTaskStatus(String latestTaskStatus) { this.latestTaskStatus = latestTaskStatus; }

    public LocalDateTime getLatestTaskTime() { return latestTaskTime; }
    public void setLatestTaskTime(LocalDateTime latestTaskTime) { this.latestTaskTime = latestTaskTime; }

    public int getTotalTaskCount() { return totalTaskCount; }
    public void setTotalTaskCount(int totalTaskCount) { this.totalTaskCount = totalTaskCount; }

    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }

    public int getPendingCount() { return pendingCount; }
    public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }

    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
}
