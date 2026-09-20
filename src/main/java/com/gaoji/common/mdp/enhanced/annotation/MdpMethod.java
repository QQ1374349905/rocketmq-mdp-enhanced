package com.gaoji.common.mdp.enhanced.annotation;

import java.lang.annotation.*;

/**
 * Enhanced MDP Method annotation (替代 @MdpMethod)
 * 支持延迟消息
 * <p>
 * 用法：标注在接口方法上
 * <p>
 * 示例:
 *
 * @MdpMethod(isSync = false)
 * void onMessage(OrderInfo order);
 * @MdpMethod(isSync = false, supportDelay = true)
 * void onMessageDelayed(OrderInfo order, int delayLevel);
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MdpMethod {

    /**
     * 是否同步发送
     * true: 同步发送，等待结果
     * false: 异步发送，不等待结果
     */
    boolean isSync() default true;

    /**
     * 是否支持延迟消息
     * 如果为 true，方法需要额外的 int delayLevel 参数
     */
    boolean supportDelay() default false;

    /**
     * 消息标签（tags）
     */
    String tags() default "";
}
