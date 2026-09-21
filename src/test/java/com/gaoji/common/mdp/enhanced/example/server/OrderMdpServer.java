package com.gaoji.common.mdp.enhanced.example.server;

import com.gaoji.common.mdp.enhanced.annotation.Idempotent;
import com.gaoji.common.mdp.enhanced.annotation.MdpServer;
import com.gaoji.common.mdp.enhanced.example.domain.OrderInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 订单MDP消费者示例
 *
 * 使用 @MdpServer 标注，框架会自动注册消息监听器
 * service 参数用于生成:
 *   - topic: "order_service"
 *   - group: "order_service_consumer_group"
 *
 * 幂等性保证:
 * - 使用 @Idempotent 注解防止消息重复消费
 * - 基于订单ID进行去重
 * - 去重记录保留24小时
 */
@Component
@MdpServer(service = "order.service")
public class OrderMdpServer {

    private static final Logger logger = LoggerFactory.getLogger(OrderMdpServer.class);

    /**
     * 同步发送订单消息处理方法
     * 方法名必须与客户端接口方法名一致
     *
     * 幂等性保证:
     * - keyExpression = "#order.orderId" 使用订单ID作为业务唯一键
     * - timeout = 86400 去重记录保留24小时
     * - duplicateStrategy = SKIP 重复消息直接跳过，返回消费成功
     */
    @Idempotent(keyExpression = "#order.orderId", timeout = 86400)
    public void sendOrder(OrderInfo order) {
        logger.info("收到同步订单消息: {}", order);
        processOrder(order, "同步消息");
    }

    /**
     * 异步发送订单消息处理方法
     */
    @Idempotent(keyExpression = "#order.orderId", timeout = 86400)
    public void sendOrderAsync(OrderInfo order) {
        logger.info("收到异步订单消息: {}", order);
        processOrder(order, "异步消息");
    }

    /**
     * 延迟订单消息处理方法
     */
    @Idempotent(keyExpression = "#order.orderId", timeout = 86400)
    public void sendOrderDelayed(OrderInfo order) {
        logger.info("收到延迟订单消息: {}", order);
        processOrder(order, "延迟消息");
    }

    /**
     * VIP订单消息处理方法
     */
    @Idempotent(keyExpression = "#order.orderId", timeout = 86400)
    public void sendVipOrder(OrderInfo order) {
        logger.info("收到VIP订单消息: {}", order);
        processOrder(order, "VIP消息");
    }

    private void processOrder(OrderInfo order, String messageType) {
        try {
            logger.info("开始处理订单 [{}]: orderId={}, userId={}, productName={}, amount={}",
                messageType, order.getOrderId(), order.getUserId(),
                order.getProductName(), order.getAmount());

            // 模拟业务处理
            // - 库存扣减
            // - 支付处理
            // - 发送通知
            // 等等...

            order.setStatus("PROCESSED");
            logger.info("订单处理成功 [{}]: orderId={}", messageType, order.getOrderId());
        } catch (Exception e) {
            logger.error("订单处理失败 [{}]: orderId={}", messageType, order.getOrderId(), e);
            throw new RuntimeException("订单处理失败", e);
        }
    }
}
