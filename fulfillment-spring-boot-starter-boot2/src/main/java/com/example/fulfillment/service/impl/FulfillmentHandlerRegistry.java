package com.example.fulfillment.service.impl;

import com.example.fulfillment.common.enums.LifecycleActionCodes;
import com.example.fulfillment.spi.FulfillmentHandler;
import com.example.fulfillment.spi.FulfillmentHandlerMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handler 注册表
 * <p>
 * 支持按 benefitTypeCode + actionCode 联合匹配 Handler，使用缓存提高查找性能。
 * <p>
 * 支持两种 Handler 注册方式（可混合使用）：
 * <ol>
 *   <li><b>注解式注册</b>：在 Handler 类上标注 {@link FulfillmentHandlerMapping} 注解，
 *       声明 {@code benefitType} 和 {@code action}。注解优先级高于编程式</li>
 *   <li><b>编程式注册</b>：覆盖 {@link FulfillmentHandler#supports(String)} 和
 *       {@link FulfillmentHandler#supports(String, String)} 方法</li>
 * </ol>
 * <p>
 * 匹配策略（两阶段优先级）：
 * <ol>
 *   <li><b>优先匹配动作专用 Handler</b>：明确绑定到某个 actionCode 的处理器</li>
 *   <li><b>回退匹配通用 Handler</b>：处理某权益类型的所有动作</li>
 * </ol>
 * <p>
 * 由 {@link com.example.fulfillment.config.FulfillmentCoreAutoConfiguration} 自动装配。
 */
public class FulfillmentHandlerRegistry {
    
    private static final Logger log = LoggerFactory.getLogger(FulfillmentHandlerRegistry.class);
    
    /** 注解中表示"通用 Handler，匹配所有动作"的通配符 */
    private static final String WILDCARD_ACTION = "*";
    
    private final List<FulfillmentHandler> handlers;
    private final Map<String, FulfillmentHandler> handlerCache = new ConcurrentHashMap<>();

    public FulfillmentHandlerRegistry(List<FulfillmentHandler> handlers) {
        this.handlers = handlers != null ? handlers : Collections.emptyList();
        log.info("初始化 Handler 注册表，共 {} 个 Handler", this.handlers.size());
        for (FulfillmentHandler handler : this.handlers) {
            FulfillmentHandlerMapping mapping = handler.getClass().getAnnotation(FulfillmentHandlerMapping.class);
            if (mapping != null) {
                log.info("  [注解] {} -> benefitType={}, action={}",
                        handler.getClass().getSimpleName(), mapping.benefitType(), mapping.action());
            } else {
                log.info("  [编程] {}", handler.getClass().getSimpleName());
            }
        }
    }

    /**
     * 根据权益类型编码获取对应的 Handler（默认使用 GRANT 动作）
     */
    public FulfillmentHandler getHandler(String benefitTypeCode) {
        return getHandler(benefitTypeCode, LifecycleActionCodes.GRANT);
    }

    /**
     * 根据权益类型编码和动作编码联合获取对应的 Handler
     * 
     * @param benefitTypeCode 权益类型编码（字符串）
     * @param actionCode      生命周期动作编码（如 "GRANT"、"REVOKE"、"RENEW"）
     * @return Handler 实例
     * @throws IllegalArgumentException 如果未找到对应的 Handler
     */
    public FulfillmentHandler getHandler(String benefitTypeCode, String actionCode) {
        if (benefitTypeCode == null || benefitTypeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("benefitTypeCode cannot be null or blank");
        }
        if (actionCode == null || actionCode.trim().isEmpty()) {
            actionCode = LifecycleActionCodes.GRANT;
        }
        
        String cacheKey = benefitTypeCode + ":" + actionCode;
        
        FulfillmentHandler handler = handlerCache.get(cacheKey);
        if (handler != null) {
            return handler;
        }
        
        final String finalActionCode = actionCode;

        // 阶段 1：优先查找动作专用 Handler
        handler = findActionSpecificHandler(benefitTypeCode, finalActionCode);

        // 阶段 2：回退到通用 Handler
        if (handler == null) {
            handler = findGenericHandler(benefitTypeCode, finalActionCode);
        }
        
        if (handler != null) {
            handlerCache.put(cacheKey, handler);
            log.debug("匹配 Handler: {}:{} -> {}", benefitTypeCode, actionCode, handler.getClass().getSimpleName());
        } else {
            log.error("未找到对应的 Handler: benefitTypeCode={}, actionCode={}", benefitTypeCode, actionCode);
            throw new IllegalArgumentException(
                    "No handler found for benefitTypeCode=" + benefitTypeCode + ", actionCode=" + actionCode);
        }
        
        return handler;
    }

    /**
     * 查找动作专用 Handler（阶段 1）
     * <p>
     * 同时支持注解式（{@code @FulfillmentHandlerMapping(action="REVOKE")}）和
     * 编程式（{@code actionSpecific()=true}）两种判断方式。
     */
    private FulfillmentHandler findActionSpecificHandler(String benefitTypeCode, String actionCode) {
        for (FulfillmentHandler h : handlers) {
            FulfillmentHandlerMapping mapping = h.getClass().getAnnotation(FulfillmentHandlerMapping.class);
            if (mapping != null) {
                // 注解式：action 不是通配符 → 动作专用
                if (!WILDCARD_ACTION.equals(mapping.action())
                        && mapping.benefitType().equals(benefitTypeCode)
                        && mapping.action().equals(actionCode)) {
                    return h;
                }
            } else {
                // 编程式：actionSpecific()=true 且 supports(type, action)=true
                if (h.actionSpecific() && h.supports(benefitTypeCode, actionCode)) {
                    return h;
                }
            }
        }
        return null;
    }

    /**
     * 查找通用 Handler（阶段 2）
     * <p>
     * 同时支持注解式（{@code @FulfillmentHandlerMapping(action="*")}）和
     * 编程式（{@code actionSpecific()=false}）两种判断方式。
     */
    private FulfillmentHandler findGenericHandler(String benefitTypeCode, String actionCode) {
        for (FulfillmentHandler h : handlers) {
            FulfillmentHandlerMapping mapping = h.getClass().getAnnotation(FulfillmentHandlerMapping.class);
            if (mapping != null) {
                // 注解式：action 是通配符 → 通用 Handler，只需类型匹配
                if (WILDCARD_ACTION.equals(mapping.action())
                        && mapping.benefitType().equals(benefitTypeCode)) {
                    return h;
                }
            } else {
                // 编程式：actionSpecific()=false 且 supports(type, action)=true
                if (!h.actionSpecific() && h.supports(benefitTypeCode, actionCode)) {
                    return h;
                }
            }
        }
        return null;
    }
}
