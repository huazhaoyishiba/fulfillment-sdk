package com.example.fulfillment.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 支付确认请求
 * <p>
 * 注意：benefitTypeCode 使用字符串类型，支持自定义权益类型。
 * 内置类型可使用枚举名称（如 "MEMBERSHIP"、"FILE_ACCESS"），自定义类型可使用任意字符串。
 */
public class ConfirmPaymentRequest {
    @NotBlank
    private String bizOrderNo;
    @NotBlank
    private String bizUserRef;
    @NotBlank
    private String paymentNo;
    @NotBlank
    private String channelCode;
    @NotNull
    private BigDecimal paidAmount;
    /**
     * 幂等键（可选，如果不提供则 SDK 自动生成）
     */
    private String idempotentKey;
    /**
     * 权益类型编码（字符串）
     * <p>
     * 内置类型：MEMBERSHIP、FILE_ACCESS、API_QUOTA、LICENSE_KEY、CUSTOM
     * 自定义类型：可使用任意字符串，如 "COUPON"、"POINTS" 等
     */
    @NotBlank(message = "权益类型编码不能为空")
    private String benefitTypeCode;
    @NotBlank
    private String benefitConfigSnapshot;

    public String getBizOrderNo() { return bizOrderNo; }
    public void setBizOrderNo(String bizOrderNo) { this.bizOrderNo = bizOrderNo; }
    public String getBizUserRef() { return bizUserRef; }
    public void setBizUserRef(String bizUserRef) { this.bizUserRef = bizUserRef; }
    public String getPaymentNo() { return paymentNo; }
    public void setPaymentNo(String paymentNo) { this.paymentNo = paymentNo; }
    public String getChannelCode() { return channelCode; }
    public void setChannelCode(String channelCode) { this.channelCode = channelCode; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }
    public String getIdempotentKey() { return idempotentKey; }
    public void setIdempotentKey(String idempotentKey) { this.idempotentKey = idempotentKey; }
    public String getBenefitTypeCode() { return benefitTypeCode; }
    public void setBenefitTypeCode(String benefitTypeCode) { this.benefitTypeCode = benefitTypeCode; }
    public String getBenefitConfigSnapshot() { return benefitConfigSnapshot; }
    public void setBenefitConfigSnapshot(String benefitConfigSnapshot) { this.benefitConfigSnapshot = benefitConfigSnapshot; }
}
