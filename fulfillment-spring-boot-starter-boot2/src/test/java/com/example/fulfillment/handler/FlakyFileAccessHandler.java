package com.example.fulfillment.handler;

import com.example.fulfillment.common.enums.ErrorCode;
import com.example.fulfillment.domain.entity.FulfillmentTask;
import com.example.fulfillment.service.handler.HandlerResult;
import com.example.fulfillment.spi.FulfillmentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模拟一个不稳定的文件访问 Handler，前几次失败，最后成功
 */
@Component
public class FlakyFileAccessHandler implements FulfillmentHandler {

    private static final Logger log = LoggerFactory.getLogger(FlakyFileAccessHandler.class);
    private static final int MAX_FAILURES = 1; // 失败 1 次后成功

    // 使用静态计数器模拟多次调用间的状态
    private static final AtomicInteger attemptCounter = new AtomicInteger(0);

    @Override
    public boolean supports(String benefitTypeCode) {
        return "FILE_ACCESS".equals(benefitTypeCode);
    }

    @Override
    public HandlerResult fulfill(FulfillmentTask task) {
        int currentAttempt = attemptCounter.incrementAndGet();
        log.info("FlakyFileAccessHandler 尝试执行: taskNo={}, attempt={}", task.getTaskNo(), currentAttempt);

        if (currentAttempt <= MAX_FAILURES) {
            log.warn("FlakyFileAccessHandler 模拟失败: taskNo={}, attempt={}", task.getTaskNo(), currentAttempt);
            return HandlerResult.fail(ErrorCode.DOWNSTREAM_UNAVAILABLE.getCode(), "模拟下游服务不可用");
        } else {
            log.info("FlakyFileAccessHandler 模拟成功: taskNo={}, attempt={}", task.getTaskNo(), currentAttempt);
            // 重置计数器，以便下一个测试用例可以重新开始模拟失败
            attemptCounter.set(0);
            return HandlerResult.success("文件访问权限已授予，尝试次数: " + currentAttempt);
        }
    }
}
