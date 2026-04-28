package com.example.fulfillment.handler;

import com.example.fulfillment.common.enums.LifecycleActionCodes;
import com.example.fulfillment.domain.entity.FulfillmentTask;
import com.example.fulfillment.service.handler.HandlerResult;
import com.example.fulfillment.spi.FulfillmentHandler;
import org.springframework.stereotype.Component;

/**
 * 测试用的会员撤销 Handler
 * <p>
 * 演示动作专用 Handler 的实现方式。
 * <p>
 * 设计要点：
 * <ul>
 *   <li>{@link #actionSpecific()} 返回 true，明确标识为动作专用 Handler</li>
 *   <li>{@link #supports(String)} 返回 false，不参与通用类型匹配</li>
 *   <li>{@link #supports(String, String)} 仅在 MEMBERSHIP + REVOKE 时返回 true</li>
 *   <li>Registry 优先匹配动作专用 Handler（actionSpecific()=true），回退到通用 Handler</li>
 * </ul>
 */
@Component
public class TestMembershipRevokeHandler implements FulfillmentHandler {

    /**
     * 标识为动作专用 Handler
     */
    @Override
    public boolean actionSpecific() {
        return true;
    }

    /**
     * 不参与通用类型匹配（此 Handler 仅处理 REVOKE 动作）
     */
    @Override
    public boolean supports(String benefitTypeCode) {
        return false;
    }

    /**
     * 仅匹配 MEMBERSHIP + REVOKE 组合
     */
    @Override
    public boolean supports(String benefitTypeCode, String actionCode) {
        return "MEMBERSHIP".equals(benefitTypeCode) && LifecycleActionCodes.REVOKE.equals(actionCode);
    }

    @Override
    public HandlerResult fulfill(FulfillmentTask task) {
        return HandlerResult.success("会员权益已撤销: " + task.getBenefitConfigSnapshot());
    }
}
