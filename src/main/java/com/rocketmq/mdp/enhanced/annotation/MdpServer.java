package com.rocketmq.mdp.enhanced.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * Enhanced MDP Consumer annotation (替代 @MdpS)
 * 支持延迟消息和灵活参数转换
 *
 * 用法：标注在类上，类中定义 onMessage 方法处理消息
 *
 * 示例:
 * @MdpServer(service = "nldb.trade.order")
 * public class OrderConsumer {
 *     public void onMessage(OrderNotify order) {
 *         // 处理消息
 *     }
 * }
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface MdpServer {

    /**
     * 服务名称，用于自动生成 topic 和 group
     * topic = service
     * group = service + "_consumer_group"
     */
    String service();

    /**
     * 可选：手动指定 topic（覆盖自动生成）
     */
    String topic() default "";

    /**
     * 可选：手动指定 consumer group（覆盖自动生成）
     */
    String group() default "";

    /**
     * 最大消费线程数
     */
    int maxThreads() default 20;

    /**
     * 消息模式：CLUSTERING（集群）或 BROADCASTING（广播）
     */
    String messageModel() default "CLUSTERING";

    /**
     * 是否支持灵活参数转换（不同包名自动转换）
     */
    boolean flexibleConversion() default true;
}
