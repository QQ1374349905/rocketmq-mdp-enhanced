package com.gaoji.common.mdp.enhanced.annotation;

import java.lang.annotation.*;

/**
 * Enhanced MDP Producer annotation (替代 @MdpC)
 * 支持延迟消息发送
 *
 * 用法：标注在接口上，框架会自动生成实现类
 *
 * 示例:
 * @MdpCEnhanced(service = "nldb.trade.order.ret")
 * public interface OrderRetMdp {
 *     @MdpMethodEnhanced(isSync = false)
 *     void onMessage(TradeMqResponse response);
 *
 *     @MdpMethodEnhanced(isSync = false, supportDelay = true)
 *     void onMessageDelayed(TradeMqResponse response, int delayLevel);
 * }
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MdpClient {

    /**
     * 服务名称，用于自动生成 topic
     * topic = service
     */
    String service();

    /**
     * 可选：手动指定 topic（覆盖自动生成）
     */
    String topic() default "";

    /**
     * 发送超时时间（毫秒）
     */
    int sendTimeout() default 3000;

    /**
     * 是否支持灵活参数转换（不同包名自动转换）
     */
    boolean flexibleConversion() default true;
}
