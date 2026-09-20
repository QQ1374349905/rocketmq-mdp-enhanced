package com.gaoji.common.mdp.enhanced.example.client;

import com.gaoji.common.mdp.enhanced.annotation.MdpClient;
import com.gaoji.common.mdp.enhanced.annotation.MdpMethod;
import com.gaoji.common.mdp.enhanced.enums.DelayLevel;
import com.gaoji.common.mdp.enhanced.example.domain.OrderInfo;

/**
 * 订单MDP生产者接口示例
 *
 * 使用 @MdpClient 标注，框架会自动生成实现类
 * service 参数用于生成 topic: "order.service"
 */
@MdpClient(
    service = "order.service",
    sendTimeout = 5000,
    flexibleConversion = true
)
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
     * 发送延迟订单消息
     * supportDelay=true 表示支持延迟消息
     * 使用 DelayLevel 枚举指定延迟时间
     */
    @MdpMethod(isSync = false, supportDelay = true)
    void sendOrderDelayed(OrderInfo order, DelayLevel delayLevel);

    /**
     * 带标签的消息发送
     * tags 用于消息过滤
     */
    @MdpMethod(isSync = false, tags = "VIP_ORDER")
    void sendVipOrder(OrderInfo order);
}
