package com.example.fulfillment.spi;

import java.lang.annotation.*;

/**
 * 注解式 Handler 注册映射
 * <p>
 * 在 {@link FulfillmentHandler} 实现类上标注此注解，即可声明该 Handler 处理的
 * 权益类型和生命周期动作组合，免去手动实现 {@link FulfillmentHandler#supports(String)}
 * 和 {@link FulfillmentHandler#supports(String, String)} 方法。
 * <p>
 * 使用此注解后，SDK 的 Handler 注册表会自动根据注解元数据进行匹配，
 * 无需开发者自行编写匹配逻辑。
 *
 * <h3>匹配规则</h3>
 * <ul>
 *   <li>{@code benefitType} 必须精确匹配（区分大小写）</li>
 *   <li>{@code action = "*"}（默认）表示通用 Handler，匹配所有动作</li>
 *   <li>{@code action} 指定具体动作值时（如 "REVOKE"），表示动作专用 Handler，优先级高于通用 Handler</li>
 * </ul>
 *
 * <h3>优先级</h3>
 * <ol>
 *   <li>动作专用 Handler（{@code action != "*"}）优先于通用 Handler</li>
 *   <li>注解式注册与编程式注册（重写 supports 方法）可共存，匹配逻辑一致</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 *
 * <p>通用 Handler（处理 MEMBERSHIP 的所有动作）：
 * <pre>{@code
 * @Component
 * @FulfillmentHandlerMapping(benefitType = "MEMBERSHIP")
 * public class MembershipHandler implements FulfillmentHandler {
 *     @Override
 *     public boolean supports(String benefitTypeCode) { return true; }
 *     // supports 会被注解覆盖，但为了接口完整性仍需保留
 *
 *     @Override
 *     public HandlerResult fulfill(FulfillmentTask task) {
 *         return HandlerResult.success("会员开通30天");
 *     }
 * }
 * }</pre>
 *
 * <p>动作专用 Handler（仅处理 MEMBERSHIP + REVOKE）：
 * <pre>{@code
 * @Component
 * @FulfillmentHandlerMapping(benefitType = "MEMBERSHIP", action = "REVOKE")
 * public class MembershipRevokeHandler implements FulfillmentHandler {
 *     @Override
 *     public boolean supports(String benefitTypeCode) { return false; }
 *
 *     @Override
 *     public HandlerResult fulfill(FulfillmentTask task) {
 *         return HandlerResult.success("会员权益已撤销");
 *     }
 * }
 * }</pre>
 *
 * @see FulfillmentHandler
 * @see com.example.fulfillment.common.enums.LifecycleActionCodes
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FulfillmentHandlerMapping {

    /**
     * 权益类型编码（必须指定）
     * <p>
     * 例如：{@code "MEMBERSHIP"}、{@code "FILE_ACCESS"}、{@code "API_QUOTA"} 等。
     * 匹配时区分大小写。
     */
    String benefitType();

    /**
     * 生命周期动作编码（默认 {@code "*"} 表示通用 Handler）
     * <p>
     * 指定具体动作值（如 {@code "REVOKE"}、{@code "RENEW"}）时，该 Handler 将被视为
     * 动作专用 Handler，在匹配时优先于通用 Handler。
     *
     * @see com.example.fulfillment.common.enums.LifecycleActionCodes
     */
    String action() default "*";
}
