package com.example.fulfillment.handler;

import com.example.fulfillment.domain.entity.FulfillmentTask;
import com.example.fulfillment.service.handler.HandlerResult;
import com.example.fulfillment.spi.FulfillmentHandler;
import org.springframework.stereotype.Component;

/**
 * 测试用的 API 配额 Handler
 */
@Component
public class TestApiQuotaHandler implements FulfillmentHandler {
    @Override
    public boolean supports(String benefitTypeCode) {
        return "API_QUOTA".equals(benefitTypeCode);
    }

    @Override
    public HandlerResult fulfill(FulfillmentTask task) {
        return HandlerResult.success("API 配额已增加: " + task.getBenefitConfigSnapshot());
    }
}
