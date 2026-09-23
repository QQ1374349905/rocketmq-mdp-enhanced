package com.example.mdp.client;

import com.rocketmq.mdp.enhanced.annotation.MdpClient;
import com.rocketmq.mdp.enhanced.annotation.MdpMethod;
import com.rocketmq.mdp.enhanced.enums.DelayLevel;
import com.example.mdp.domain.OrderInfo;

/**
 * 订单MDP生产者接口示例
 *
 * 使用 @MdpClient 标注，框架会自动生成实现类
 * service 参数用于生成 topic: "order.service"
 */
@MdpClient(service = "order.service")
public interface OrderMdpClient {

    /**
     * 同步发送订单消息
     * isSync=true 表示等待发送结果
     */
    @MdpMethod(isSync = true)
    void sendOrder(OrderInfo order);

    /**
     * 异步发送订单消息
     * isSync=false 表示不等待发送结果
     */
    @MdpMethod(isSync = false)
    void sendOrderAsync(OrderInfo order);

    /**
     * 发送延迟订单消息（延迟10秒）
     * delayLevel 指定延迟级别
     */
    @MdpMethod(isSync = false, delayLevel = DelayLevel.SECONDS_10)
    void sendOrderDelayed(OrderInfo order);

    /**
     * 带标签的消息发送
     * tags 用于消息过滤
     */
    @MdpMethod(isSync = false, tags = "VIP_ORDER")
    void sendVipOrder(OrderInfo order);
}
