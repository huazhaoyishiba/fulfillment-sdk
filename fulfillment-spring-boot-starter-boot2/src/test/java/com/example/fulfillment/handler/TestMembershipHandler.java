package com.example.fulfillment.handler;

import com.example.fulfillment.domain.entity.FulfillmentTask;
import com.example.fulfillment.service.handler.HandlerResult;
import com.example.fulfillment.spi.FulfillmentHandler;
import org.springframework.stereotype.Component;

/**
 * 测试用的会员 Handler
 */
@Component
public class TestMembershipHandler implements FulfillmentHandler {
    @Override
    public boolean supports(String benefitTypeCode) {
        return "MEMBERSHIP".equals(benefitTypeCode);
    }

    @Override
    public HandlerResult fulfill(FulfillmentTask task) {
        return HandlerResult.success("会员权益已发放: " + task.getBenefitConfigSnapshot());
    }
}
