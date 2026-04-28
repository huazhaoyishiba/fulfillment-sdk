package com.example.fulfillment.common.enums;

/**
 * 权益生命周期动作编码常量
 * <p>
 * 第一阶段支持三种动作：
 * <ul>
 *   <li>{@link #GRANT} - 发放/授予权益（默认动作，兼容现有 confirmPaidAndFulfill / manualFulfill）</li>
 *   <li>{@link #REVOKE} - 撤销/回收权益（退款、封禁等场景）</li>
 *   <li>{@link #RENEW} - 续期/续费权益（会员续费、配额刷新等场景）</li>
 * </ul>
 * <p>
 * 设计说明：使用 String 常量而非枚举，保持与 benefitTypeCode 一致的扩展风格。
 * 接入方可自行定义新的 actionCode（如 "UPGRADE"、"DOWNGRADE"），只需在 Handler 中 supports 即可。
 */
public final class LifecycleActionCodes {

    private LifecycleActionCodes() {
        // 不允许实例化
    }

    /** 发放/授予权益（默认动作） */
    public static final String GRANT = "GRANT";

    /** 撤销/回收权益 */
    public static final String REVOKE = "REVOKE";

    /** 续期/续费权益 */
    public static final String RENEW = "RENEW";
}
