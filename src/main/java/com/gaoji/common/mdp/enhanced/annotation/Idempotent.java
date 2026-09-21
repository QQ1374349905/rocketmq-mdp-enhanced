package com.gaoji.common.mdp.enhanced.annotation;

import java.lang.annotation.*;

/**
 * 幂等性注解
 *
 * 标注在消费者方法上，表示该方法需要幂等性保证
 * 框架会自动进行消息去重，防止重复消费
 *
 * 使用场景：
 * 1. 订单创建 - 防止重复创建订单
 * 2. 支付处理 - 防止重复扣款
 * 3. 库存扣减 - 防止重复扣库存
 * 4. 积分发放 - 防止重复发放积分
 *
 * 去重策略：
 * - keyExpression: SpEL表达式，从消息参数中提取业务唯一键（如订单ID）
 * - timeout: 去重记录过期时间（秒），默认24小时
 * - lockTimeout: 分布式锁超时时间（秒），默认10秒
 *
 * 示例：
 * <pre>
 * {@code @Idempotent(keyExpression = "#order.orderId", timeout = 86400)}
 * public void sendOrder(OrderInfo order) {
 *     // 业务逻辑
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 业务唯一键提取表达式（SpEL）
     *
     * 从方法参数中提取业务唯一标识
     * 示例：
     * - "#order.orderId" - 提取订单ID
     * - "#user.userId" - 提取用户ID
     * - "#order.orderId + '_' + #order.userId" - 组合键
     */
    String keyExpression();

    /**
     * 去重记录过期时间（秒）
     * 默认24小时
     *
     * 建议根据业务场景设置：
     * - 订单：86400（24小时）
     * - 支付：259200（3天）
     * - 日志：3600（1小时）
     */
    long timeout() default 86400;

    /**
     * 分布式锁超时时间（秒）
     * 默认10秒
     *
     * 防止消息并发处理时的竞态条件
     */
    long lockTimeout() default 10;

    /**
     * 去重失败时的处理策略
     * - SKIP: 跳过重复消息，返回消费成功（默认）
     * - EXCEPTION: 抛出异常，触发消息重试
     */
    DuplicateStrategy duplicateStrategy() default DuplicateStrategy.SKIP;

    enum DuplicateStrategy {
        /**
         * 跳过重复消息，返回消费成功
         */
        SKIP,

        /**
         * 抛出异常，触发消息重试
         */
        EXCEPTION
    }
}
