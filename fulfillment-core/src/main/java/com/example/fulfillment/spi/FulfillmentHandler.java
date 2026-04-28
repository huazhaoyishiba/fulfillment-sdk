package com.example.fulfillment.spi;

import com.example.fulfillment.domain.entity.FulfillmentTask;
import com.example.fulfillment.service.handler.HandlerResult;

/**
 * 权益发放处理器 SPI 接口
 * <p>
 * 接入方需要为每种权益类型（或权益类型+动作组合）实现此接口。SDK 内核会根据
 * {@link #supports(String, String)} 方法自动匹配对应的处理器并调用
 * {@link #fulfill(FulfillmentTask)} 执行发放。
 * <p>
 * 匹配优先级（两阶段）：
 * <ol>
 *   <li><b>优先匹配动作专用 Handler</b>：{@link #actionSpecific()} 返回 true 且 {@link #supports(String, String)} 返回 true，
 *       即该 Handler 明确绑定到某个 actionCode，不是通用处理器</li>
 *   <li><b>回退匹配通用 Handler</b>：{@link #supports(String, String)} 返回 true（含 default 实现回退到 supports(type)），
 *       即该 Handler 处理某个 benefitTypeCode 的所有动作</li>
 * </ol>
 * <p>
 * Handler 类型说明：
 * <ul>
 *   <li><b>通用 Handler</b>：{@link #actionSpecific()} 返回 false（默认），处理某权益类型的所有动作。
 *       例如：MembershipHandler 处理 MEMBERSHIP 的 GRANT / REVOKE / RENEW 等所有动作</li>
 *   <li><b>动作专用 Handler</b>：{@link #actionSpecific()} 返回 true，仅处理特定 type + action 组合。
 *       例如：MembershipRevokeHandler 仅处理 MEMBERSHIP + REVOKE</li>
 * </ul>
 * <p>
 * 向后兼容说明：
 * <ul>
 *   <li>已有只覆盖 {@link #supports(String)} 的 Handler 无需修改，仍能正常匹配所有 actionCode</li>
 *   <li>新 Handler 如需区分动作，只需额外覆盖 {@link #supports(String, String)}</li>
 * </ul>
 * <p>
 * 实现要求：
 * <ol>
 *   <li>实现类必须是 Spring Bean（加 {@code @Component} 或在 {@code @Configuration} 中注册）</li>
 *   <li>{@link #fulfill(FulfillmentTask)} 不应做长时间阻塞操作，超时由 SDK 配置控制</li>
 *   <li>发放的最终业务存储由实现方自己管理，SDK 只关注任务状态</li>
 *   <li>如果发放失败，返回 {@link HandlerResult#fail(String, String)}，SDK 会根据错误码决定是否重试</li>
 *   <li>方法内部抛出的异常会被 SDK 捕获并记录，无需实现方自行处理</li>
 * </ol>
 *
 * <p>示例（仅按 benefitTypeCode 匹配，兼容旧方式）：
 * <pre>{@code
 * @Component
 * public class MembershipHandler implements FulfillmentHandler {
 *     @Override
 *     public boolean supports(String benefitTypeCode) {
 *         return "MEMBERSHIP".equals(benefitTypeCode);
 *     }
 *
 *     @Override
 *     public HandlerResult fulfill(FulfillmentTask task) {
 *         String config = task.getBenefitConfigSnapshot();
 *         return HandlerResult.success("会员开通30天");
 *     }
 * }
 * }</pre>
 *
 * <p>示例（动作专用 Handler，仅处理 MEMBERSHIP + REVOKE）：
 * <pre>{@code
 * @Component
 * public class MembershipRevokeHandler implements FulfillmentHandler {
 *     @Override
 *     public boolean actionSpecific() {
 *         return true;  // 标识为动作专用 Handler
 *     }
 *
 *     @Override
 *     public boolean supports(String benefitTypeCode) {
 *         return false;  // 不参与通用类型匹配
 *     }
 *
 *     @Override
 *     public boolean supports(String benefitTypeCode, String actionCode) {
 *         return "MEMBERSHIP".equals(benefitTypeCode) && "REVOKE".equals(actionCode);
 *     }
 *
 *     @Override
 *     public HandlerResult fulfill(FulfillmentTask task) {
 *         return HandlerResult.success("会员权益已撤销");
 *     }
 * }
 * }</pre>
 */
public interface FulfillmentHandler {

    /**
     * 判断此 Handler 是否为动作专用处理器
     * <p>
     * 默认返回 false，表示这是一个通用 Handler，处理某权益类型的所有动作。
     * 如果返回 true，表示这是一个动作专用 Handler，仅处理特定的 type + action 组合。
     * <p>
     * 设计目的：避免依赖隐式约定（如 "supports(type)=false 才算专用"），
     * 让 Handler 类型更明确，降低接入方的误用风险。
     * <p>
     * 示例：
     * <ul>
     *   <li>通用 Handler：{@code actionSpecific() { return false; }}</li>
     *   <li>动作专用 Handler：{@code actionSpecific() { return true; }}</li>
     * </ul>
     *
     * @return true 如果这是动作专用 Handler，false 如果是通用 Handler（默认）
     */
    default boolean actionSpecific() {
        return false;
    }

    /**
     * 判断是否支持指定的权益类型编码（仅按类型匹配）
     * <p>
     * 注意：benefitTypeCode 是字符串类型，支持自定义权益类型。
     * 内置类型使用枚举名称（如 "MEMBERSHIP"、"FILE_ACCESS"），自定义类型可使用任意字符串。
     *
     * @param benefitTypeCode 权益类型编码（字符串）
     * @return true 如果此处理器能处理该类型
     */
    boolean supports(String benefitTypeCode);

    /**
     * 判断是否支持指定的权益类型编码与动作编码的组合（联合匹配）
     * <p>
     * 默认实现回退到 {@link #supports(String)}，即忽略 actionCode。
     * 如果 Handler 需要区分不同的生命周期动作（如 GRANT / REVOKE / RENEW），
     * 应覆盖此方法进行更精确的匹配。
     *
     * @param benefitTypeCode 权益类型编码
     * @param actionCode      生命周期动作编码（如 "GRANT"、"REVOKE"、"RENEW"）
     * @return true 如果此处理器能处理该类型+动作组合
     * @see com.example.fulfillment.common.enums.LifecycleActionCodes
     */
    default boolean supports(String benefitTypeCode, String actionCode) {
        return supports(benefitTypeCode);
    }

    /**
     * 执行权益发放（或其他生命周期动作）
     * <p>
     * SDK 会在调用此方法前设置好任务的所有上下文信息，实现方可以通过
     * {@link FulfillmentTask#getBenefitConfigSnapshot()} 获取权益配置快照，
     * 通过 {@link FulfillmentTask#getActionCode()} 获取当前动作编码。
     *
     * @param task 发放任务，包含订单号、用户标识、权益配置、动作编码等完整信息
     * @return 发放结果，成功或失败及原因
     */
    HandlerResult fulfill(FulfillmentTask task);
}
