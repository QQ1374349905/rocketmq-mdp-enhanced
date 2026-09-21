package com.gaoji.common.mdp.example.client;

import com.gaoji.common.mdp.enhanced.annotation.MdpClient;
import com.gaoji.common.mdp.enhanced.annotation.MdpMethod;
import com.gaoji.common.mdp.enhanced.enums.DelayLevel;
import com.gaoji.common.mdp.example.domain.OrderInfo;

/**
 * VIP订单MDP生产者接口
 *
 * 使用独立的 topic，消息只会被 VipOrderMdpServer 消费
 * service = "vip.order.service" 生成 topic: "vip_order_service"
 */
@MdpClient(service = "vip.order.service")
public interface VipOrderMdpClient {

    /**
     * 同步发送VIP订单消息
     */
    @MdpMethod(isSync = true)
    void sendOrder(OrderInfo order);

    /**
     * 异步发送VIP订单消息
     */
    @MdpMethod(isSync = false)
    void sendOrderAsync(OrderInfo order);

    /**
     * 发送延迟VIP订单消息
     */
    @MdpMethod(isSync = false, supportDelay = true)
    void sendOrderDelayed(OrderInfo order, DelayLevel delayLevel);

    /**
     * 发送VIP订单消息（带标签）
     */
    @MdpMethod(isSync = false, tags = "VIP_ORDER")
    void sendVipOrder(OrderInfo order);
}
