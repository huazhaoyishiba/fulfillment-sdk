package com.example.fulfillment.domain.entity;

import java.time.LocalDateTime;

/**
 * 发放任务实体
 */
public class FulfillmentTask {
    private Long id;
    private String taskNo;
    private String bizOrderNo;
    private String bizUserRef;
    private String benefitTypeCode;
    /** 生命周期动作编码（GRANT / REVOKE / RENEW 等），默认 GRANT */
    private String actionCode;
    private String benefitConfigSnapshot;
    private String status;
    private Integer retryCount;
    private Integer maxRetryCount;
    private String lastErrorCode;
    private String lastErrorMsg;
    /** 下次允许重试的时间（退避策略），仅 RETRY_WAIT 状态有效 */
    private LocalDateTime nextRetryAt;
    /** 乐观锁版本号，防止并发修改冲突 */
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public String getBenefitConfigSnapshot() { return benefitConfigSnapshot; }
    public void setBenefitConfigSnapshot(String benefitConfigSnapshot) { this.benefitConfigSnapshot = benefitConfigSnapshot; }
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
    public LocalDateTime getNextRetryAt() { return nextRetryAt; }
    public void setNextRetryAt(LocalDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return "FulfillmentTask{" +
                "id=" + id +
                ", taskNo='" + taskNo + '\'' +
                ", bizOrderNo='" + bizOrderNo + '\'' +
                ", actionCode='" + actionCode + '\'' +
                ", status='" + status + '\'' +
                ", retryCount=" + retryCount +
                ", version=" + version +
                '}';
    }
}
