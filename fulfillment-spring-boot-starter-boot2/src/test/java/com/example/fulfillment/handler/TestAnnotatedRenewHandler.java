package com.example.fulfillment.handler;

import com.example.fulfillment.domain.entity.FulfillmentTask;
import com.example.fulfillment.service.handler.HandlerResult;
import com.example.fulfillment.spi.FulfillmentHandler;
import com.example.fulfillment.spi.FulfillmentHandlerMapping;
import org.springframework.stereotype.Component;

/**
 * 测试用：使用注解式注册的 MEMBERSHIP + RENEW 专用 Handler
 * <p>
 * 演示 {@link FulfillmentHandlerMapping} 注解的用法。
 * 不需要实现 {@code supports()} 系列方法（虽然接口要求保留一个 supports(String)）。
 */
@Component
@FulfillmentHandlerMapping(benefitType = "MEMBERSHIP", action = "RENEW")
public class TestAnnotatedRenewHandler implements FulfillmentHandler {

    @Override
    public boolean supports(String benefitTypeCode) {
        // 注解式注册时此方法不会被调用，但接口要求保留
        return false;
    }

    @Override
    public HandlerResult fulfill(FulfillmentTask task) {
        return HandlerResult.success("会员已续期（注解式 Handler）: " + task.getBenefitConfigSnapshot());
    }
}
