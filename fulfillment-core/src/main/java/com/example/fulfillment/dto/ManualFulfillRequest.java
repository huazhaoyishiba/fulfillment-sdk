package com.example.fulfillment.dto;

import javax.validation.constraints.NotBlank;

/**
 * 手动补发请求
 * <p>
 * 用于人工补偿场景，每次补发生成新的发放任务。
 * 调用方必须传入唯一的 idempotentKey 保证补发幂等。
 * <p>
 * 注意：benefitTypeCode 使用字符串类型，支持自定义权益类型。
 */
public class ManualFulfillRequest {
    
    /**
     * 幂等键（必填）
     * <p>
     * 每次补发请求必须携带唯一的幂等键，防止重复补发。
     * 建议格式：MANUAL-{订单号}-{时间戳} 或由业务方自行生成。
     */
    @NotBlank(message = "幂等键不能为空")
    private String idempotentKey;
    
    @NotBlank(message = "业务订单号不能为空")
    private String bizOrderNo;
    
    @NotBlank(message = "用户标识不能为空")
    private String bizUserRef;
    
    /**
     * 权益类型编码（字符串）
     * <p>
     * 内置类型：MEMBERSHIP、FILE_ACCESS、API_QUOTA、LICENSE_KEY、CUSTOM
     * 自定义类型：可使用任意字符串，如 "COUPON"、"POINTS" 等
     */
    @NotBlank(message = "权益类型编码不能为空")
    private String benefitTypeCode;
    
    @NotBlank(message = "权益配置快照不能为空")
    private String benefitConfigSnapshot;
    
    /** 补发原因（建议必填，用于审计） */
    private String reason;
    
    /** 是否关联原任务 */
    private Boolean linkOriginalTask = false;

    public String getIdempotentKey() { return idempotentKey; }
    public void setIdempotentKey(String idempotentKey) { this.idempotentKey = idempotentKey; }
    public String getBizOrderNo() { return bizOrderNo; }
    public void setBizOrderNo(String bizOrderNo) { this.bizOrderNo = bizOrderNo; }
    public String getBizUserRef() { return bizUserRef; }
    public void setBizUserRef(String bizUserRef) { this.bizUserRef = bizUserRef; }
    public String getBenefitTypeCode() { return benefitTypeCode; }
    public void setBenefitTypeCode(String benefitTypeCode) { this.benefitTypeCode = benefitTypeCode; }
    public String getBenefitConfigSnapshot() { return benefitConfigSnapshot; }
    public void setBenefitConfigSnapshot(String benefitConfigSnapshot) { this.benefitConfigSnapshot = benefitConfigSnapshot; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Boolean getLinkOriginalTask() { return linkOriginalTask; }
    public void setLinkOriginalTask(Boolean linkOriginalTask) { this.linkOriginalTask = linkOriginalTask; }
}
