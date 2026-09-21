package com.gaoji.common.mdp.example.server;

import com.rocketmq.mdp.enhanced.annotation.MdpServer;
import com.gaoji.common.mdp.example.domain.OrderInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * VIP订单MDP消费者示例
 *
 * 使用独立的 topic 避免与普通订单消费者冲突
 * service = "vip.order.service" 生成:
 *   - topic: "vip_order_service"
 *   - group: "vip_order_service_consumer_group"
 */
@Component
@MdpServer(service = "vip.order.service")
public class VipOrderMdpServer {

    private static final Logger logger = LoggerFactory.getLogger(VipOrderMdpServer.class);

    /**
     * 同步发送订单消息处理方法
     * 方法名必须与客户端接口方法名一致
     */
    public void sendOrder(OrderInfo order) {
        logger.info("[VIP消费者] 收到同步订单消息: {}", order);
        processOrderMessage(order);
    }

    /**
     * 异步发送订单消息处理方法
     */
    public void sendOrderAsync(OrderInfo order) {
        logger.info("[VIP消费者] 收到异步订单消息: {}", order);
        processOrderMessage(order);
    }

    /**
     * 延迟订单消息处理方法
     */
    public void sendOrderDelayed(OrderInfo order) {
        logger.info("[VIP消费者] 收到延迟订单消息: {}", order);
        processOrderMessage(order);
    }

    /**
     * VIP订单消息处理方法
     */
    public void sendVipOrder(OrderInfo order) {
        logger.info("[VIP消费者] 收到VIP订单消息: {}", order);
        processOrderMessage(order);
    }

    /**
     * VIP订单特殊处理
     * 这个消费者和OrderMdpServer会同时收到消息（因为group不同）
     */
    private void processOrderMessage(OrderInfo order) {
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
