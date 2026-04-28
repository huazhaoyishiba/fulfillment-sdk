package com.example.fulfillment.dto;

import javax.validation.constraints.NotBlank;

/**
 * 统一生命周期动作执行请求
 * <p>
 * 通过 {@code actionCode} 区分不同的生命周期操作（GRANT / REVOKE / RENEW 等）。
 * SDK 会根据 {@code benefitTypeCode + actionCode} 联合匹配对应的 Handler。
 * <p>
 * 此入口不涉及支付记录写入（不同于 {@link ConfirmPaymentRequest}），适用于：
 * <ul>
 *   <li>退款后撤销权益（REVOKE）</li>
 *   <li>会员续费（RENEW）</li>
 *   <li>运营主动授予权益（GRANT，不走支付）</li>
 *   <li>自定义生命周期动作</li>
 * </ul>
 */
public class ExecuteLifecycleActionRequest {

    /**
     * 幂等键（可选）
     * <p>
     * 保证同一操作不被重复执行。
     * <ul>
     *   <li>若调用方传入：SDK 直接使用该值作为幂等键</li>
     *   <li>若未传入（null 或空）：SDK 自动按 {@code actionCode + bizOrderNo} 生成，
     *       格式为 {@code LIFECYCLE_ACTION:hash(actionCode:bizOrderNo)}。
     *       同一订单的不同动作会产生不同的幂等键，不会互相冲突</li>
     * </ul>
     * <p>
     * 建议格式（如自行生成）：{actionCode}-{订单号}-{时间戳}。
     */
    private String idempotentKey;

    @NotBlank(message = "业务订单号不能为空")
    private String bizOrderNo;

    @NotBlank(message = "用户标识不能为空")
    private String bizUserRef;

    /**
     * 权益类型编码（字符串）
     * <p>
     * 如 "MEMBERSHIP"、"API_QUOTA"、"FILE_ACCESS" 等。
     */
    @NotBlank(message = "权益类型编码不能为空")
    private String benefitTypeCode;

    /**
     * 生命周期动作编码
     * <p>
     * 第一阶段内置：GRANT / REVOKE / RENEW。
     * 可扩展自定义动作（如 "UPGRADE"、"DOWNGRADE"）。
     *
     * @see com.example.fulfillment.common.enums.LifecycleActionCodes
     */
    @NotBlank(message = "动作编码不能为空")
    private String actionCode;

    /**
     * 权益配置快照（JSON）
     * <p>
     * 快照保证重试/补发时使用的是原始配置，而非当前最新配置。
     */
    @NotBlank(message = "权益配置快照不能为空")
    private String benefitConfigSnapshot;

    /** 操作原因（建议必填，用于审计） */
    private String reason;

    // ==================== Getters / Setters ====================

    public String getIdempotentKey() { return idempotentKey; }
    public void setIdempotentKey(String idempotentKey) { this.idempotentKey = idempotentKey; }
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
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
