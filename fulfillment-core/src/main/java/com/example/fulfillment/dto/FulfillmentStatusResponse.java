package com.example.fulfillment.dto;

import java.time.LocalDateTime;

/**
 * 发放状态响应
 */
public class FulfillmentStatusResponse {
    private String taskNo;
    private String bizOrderNo;
    private String bizUserRef;
    private String benefitTypeCode;
    private String actionCode;
    private String status;
    private Integer retryCount;
    private Integer maxRetryCount;
    private String lastErrorCode;
    private String lastErrorMsg;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public String getBizOrderNo() { return bizOrderNo; }
    public void setBizOrderNo(String bizOrderNo) { this.bizOrderNo = bizOrderNo; }
    public String getBizUserRef() { return bizUserRef; }
    public void setBizUserRef(String bizUserRef) { this.bizUserRef = bizUserRef; }
    public String getBenefitTypeCode() { return benefitTypeCode; }
    public void setBenefitTypeCode(String benefitTypeCode) { this.benefitTypeCode = benefitTypeCode; }
    public String getActionCode() { return actionCode; }
    public void setActionCode(String actionCode) { this.actionCode = actionCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Integer getMaxRetryCount() { return maxRetryCount; }
    public void setMaxRetryCount(Integer maxRetryCount) { this.maxRetryCount = maxRetryCount; }
    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String lastErrorCode) { this.lastErrorCode = lastErrorCode; }
    public String getLastErrorMsg() { return lastErrorMsg; }
    public void setLastErrorMsg(String lastErrorMsg) { this.lastErrorMsg = lastErrorMsg; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
