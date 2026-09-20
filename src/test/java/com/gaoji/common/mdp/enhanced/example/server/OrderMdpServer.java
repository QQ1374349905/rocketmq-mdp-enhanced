package com.gaoji.common.mdp.enhanced.example.server;

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
 *   - topic: "order.service"
 *   - group: "order.service_consumer_group"
 */
@Component
@MdpServer(
    service = "order.service",
    maxThreads = 20,
    messageModel = "CLUSTERING",
    flexibleConversion = true
)
public class OrderMdpServer {

    private static final Logger logger = LoggerFactory.getLogger(OrderMdpServer.class);

    /**
     * 消息处理方法
     * 方法名必须是 onMessage
     *
     * 框架会自动:
     * 1. 反序列化消息
     * 2. 支持灵活参数转换（不同包名的同名类自动转换）
     * 3. 调用此方法处理消息
     */
    public void onMessage(OrderInfo order) {
        logger.info("收到订单消息: {}", order);

        try {
            // 处理订单业务逻辑
            processOrder(order);
            logger.info("订单处理成功: orderId={}", order.getOrderId());
        } catch (Exception e) {
            logger.error("订单处理失败: orderId={}", order.getOrderId(), e);
            throw new RuntimeException("订单处理失败", e);
        }
    }

    private void processOrder(OrderInfo order) {
        // 模拟业务处理
        logger.info("开始处理订单: orderId={}, userId={}, amount={}",
            order.getOrderId(), order.getUserId(), order.getAmount());

        // 这里可以添加实际的业务逻辑:
        // - 库存扣减
        // - 支付处理
        // - 发送通知
        // 等等...

        order.setStatus("PROCESSED");
    }
}
