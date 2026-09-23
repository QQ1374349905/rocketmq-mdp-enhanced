package com.example.mdp.server;

import com.rocketmq.mdp.enhanced.annotation.Idempotent;
import com.rocketmq.mdp.enhanced.annotation.MdpServer;
import com.example.mdp.domain.OrderInfo;
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
     * - key = "orderId" 使用订单ID作为业务唯一键
     * - timeout = 86400 去重记录保留24小时
     */
    @Idempotent(key = "orderId", timeout = 86400)
    public void sendOrder(OrderInfo order) {
        logger.info("收到同步订单消息: {}", order);
        processOrder(order, "同步消息");
    }

    /**
     * 异步发送订单消息处理方法
     */
    @Idempotent(key = "orderId", timeout = 86400)
    public void sendOrderAsync(OrderInfo order) {
        logger.info("收到异步订单消息: {}", order);
        processOrder(order, "异步消息");
    }

    /**
     * 延迟订单消息处理方法
     */
    @Idempotent(key = "orderId", timeout = 86400)
    public void sendOrderDelayed(OrderInfo order) {
        logger.info("收到延迟订单消息: {}", order);
        processOrder(order, "延迟消息");
    }

    /**
     * VIP订单消息处理方法
     */
    @Idempotent(key = "orderId", timeout = 86400)
    public void sendVipOrder(OrderInfo order) {
        logger.info("收到VIP订单消息: {}", order);
        processOrder(order, "VIP消息");
    }

    /**
     * 普通消息处理方法（使用默认 MD5 模式）
     *
     * 不指定 key 时，框架会自动：
     * 1. 将参数对象序列化为 JSON
     * 2. 计算 JSON 的 MD5 值作为业务键
     * 3. 基于 MD5 进行幂等性去重
     *
     * 适用场景：
     * - 消息没有明确的业务唯一键
     * - 完全基于消息内容去重
     */
    @Idempotent  // 默认使用参数的 MD5 值
    public void sendNotification(OrderInfo order) {
        logger.info("收到通知消息: {}", order);
        logger.info("发送通知给用户: userId={}, orderId={}", order.getUserId(), order.getOrderId());
    }

    /**
     * 接收DTO订单消息（同步）- 测试跨包参数转换
     * <p>
     * 客户端发送: OrderDTO (com.example.mdp.dto.OrderDTO)
     * 服务端接收: OrderInfo (com.example.mdp.domain.OrderInfo)
     * <p>
     * 框架会自动进行跨包类型转换:
     * 1. 检测类名不匹配（OrderDTO vs OrderInfo）
     * 2. 使用 ParameterConverter 基于字段名进行转换
     * 3. 自动映射相同名称的字段
     */
    @Idempotent(key = "orderId")
    public void sendOrderFromDto(OrderInfo order) {
        logger.info("收到DTO转换订单消息（同步）: {}", order);
        logger.info("  ✅ 成功将 OrderDTO (com.example.mdp.dto.OrderDTO) 转换为 OrderInfo");
        processOrder(order, "DTO转换同步消息");
    }

    /**
     * 接收DTO订单消息（异步）- 测试跨包参数转换
     */
    @Idempotent(key = "orderId")
    public void sendOrderFromDtoAsync(OrderInfo order) {
        logger.info("收到DTO转换订单消息（异步）: {}", order);
        logger.info("  ✅ 成功将 OrderDTO (com.example.mdp.dto.OrderDTO) 转换为 OrderInfo");
        processOrder(order, "DTO转换异步消息");
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
