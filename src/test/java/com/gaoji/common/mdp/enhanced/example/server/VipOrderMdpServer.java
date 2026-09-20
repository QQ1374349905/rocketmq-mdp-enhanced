package com.gaoji.common.mdp.enhanced.example.server;

import com.gaoji.common.mdp.enhanced.annotation.MdpServer;
import com.gaoji.common.mdp.enhanced.example.domain.OrderInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * VIP订单MDP消费者示例
 *
 * 演示如何使用不同的group消费同一个topic
 * 可以实现消息的多重消费（不同业务逻辑处理同一条消息）
 */
@Component
@MdpServer(service = "order.service")
public class VipOrderMdpServer {

    private static final Logger logger = LoggerFactory.getLogger(VipOrderMdpServer.class);

    /**
     * VIP订单特殊处理
     * 这个消费者和OrderMdpServer会同时收到消息（因为group不同）
     */
    public void onMessage(OrderInfo order) {
        logger.info("[VIP消费者] 收到订单消息: {}", order);

        // VIP订单的特殊处理逻辑
        if (isVipOrder(order)) {
            logger.info("[VIP消费者] 检测到VIP订单，进行特殊处理: orderId={}", order.getOrderId());

            // VIP专属处理：
            // - 优先配送
            // - 赠送积分
            // - 发送专属客服通知
            processVipOrder(order);

            logger.info("[VIP消费者] VIP订单处理完成: orderId={}", order.getOrderId());
        } else {
            logger.info("[VIP消费者] 普通订单，跳过VIP处理: orderId={}", order.getOrderId());
        }
    }

    private boolean isVipOrder(OrderInfo order) {
        // 判断是否为VIP订单
        return order.getUserId() != null && order.getUserId().startsWith("VIP_");
    }

    private void processVipOrder(OrderInfo order) {
        logger.info("[VIP消费者] 执行VIP特权:");
        logger.info("  - 优先配送安排");
        logger.info("  - 赠送积分: {}", order.getAmount().intValue() * 2);
        logger.info("  - 通知专属客服");
    }
}
