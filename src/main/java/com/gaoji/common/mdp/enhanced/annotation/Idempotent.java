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
 * - 默认：使用整个参数对象的 MD5 值作为业务键（推荐，零配置）
 * - 可选：使用 key 指定字段路径提取业务键（通过反射调用 getter 方法）
 *
 * 示例1（推荐 - 使用默认 MD5）：
 * <pre>
 * {@code @Idempotent}
 * public void sendOrder(OrderInfo order) {
 *     // 自动使用 order 对象的 MD5 去重
 * }
 * </pre>
 *
 * 示例2（可选 - 指定字段路径）：
 * <pre>
 * {@code @Idempotent(key = "orderId")}
 * public void sendOrder(OrderInfo order) {
 *     // 调用 order.getOrderId() 作为业务键
 * }
 * </pre>
 *
 * 示例3（支持嵌套路径）：
 * <pre>
 * {@code @Idempotent(key = "user.userId")}
 * public void handleMessage(RequestInfo request) {
 *     // 调用 request.getUser().getUserId()
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /**
     * 是否启用幂等性
     * 默认 true
     *
     * 设置为 false 可以临时禁用某个方法的幂等性检查
     */
    boolean enabled() default true;

    /**
     * 业务唯一键字段路径
     *
     * 通过反射调用参数对象的 getter 方法提取业务键
     *
     * 支持格式：
     * - 简单字段：key = "orderId" → 调用 getOrderId()
     * - 嵌套字段：key = "user.userId" → 调用 getUser().getUserId()
     * - 多层嵌套：key = "order.user.id" → 调用 getOrder().getUser().getId()
     *
     * 如果不指定（默认为空字符串），则自动使用参数的 MD5 值作为业务键（推荐）
     *
     * 注意：
     * - 字段名自动转换为标准 getter 方法名（首字母大写 + get 前缀）
     * - 如果 getter 调用失败，会自动降级使用 MD5
     */
    String key() default "";

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
