package com.rocketmq.mdp.enhanced.annotation;

import com.rocketmq.mdp.enhanced.enums.DelayLevel;

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
 * @MdpMethod(isSync = false, delayLevel = DelayLevel.SECONDS_10)
 * void onMessageDelayed(OrderInfo order);
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
     * 延迟级别
     * 默认 NONE（不延迟）
     */
    DelayLevel delayLevel() default DelayLevel.NONE;

    /**
     * 消息标签（tags）
     */
    String tags() default "";
}
