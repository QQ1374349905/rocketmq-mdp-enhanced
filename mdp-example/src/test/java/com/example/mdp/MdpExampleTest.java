package com.example.mdp;

import com.rocketmq.mdp.enhanced.enums.DelayLevel;
import com.example.mdp.client.OrderMdpClient;
import com.example.mdp.client.VipOrderMdpClient;
import com.example.mdp.domain.OrderInfo;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * MDP框架使用示例测试
 * <p>
 * 演示如何使用 @MdpClient 和 @MdpServer 进行消息发送和接收
 * <p>
 * 测试场景说明:
 * 1. 普通订单场景：使用 OrderMdpClient 发送，OrderMdpServer 消费（topic: order_service）
 * 2. VIP订单场景：使用 VipOrderMdpClient 发送，VipOrderMdpServer 消费（topic: vip_order_service）
 * 3. 同步/异步发送场景：测试消息发送的同步和异步模式
 * 4. 延迟消息场景：测试延迟消息投递
 * 5. 带标签消息场景：测试消息标签过滤
 * 6. 批量发送场景：测试高并发消息发送
 * 7. 灵活参数转换场景：测试不同包名的同名类自动转换
 * <p>
 * 架构设计原则:
 * - 一个 topic 对应一个消费者类，避免消息路由冲突
 * - 客户端方法名必须与服务端方法名一致
 * - 框架根据消息中的 methodName 动态路由到对应的服务端方法
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = MdpExampleApplication.class)
public class MdpExampleTest {

    @Autowired
    private OrderMdpClient orderMdpClient;

    @Autowired
    private VipOrderMdpClient vipOrderMdpClient;

    /**
     * 场景1: 测试同步发送普通订单消息
     */
    @Test
    public void testSendOrderSync() {
        OrderInfo order = new OrderInfo(
                "ORDER001",
                "USER001",
                "iPhone 15 Pro",
                8999.00
        );

        System.out.println("发送同步订单消息: " + order);
        orderMdpClient.sendOrder(order);
        System.out.println("同步消息发送完成");
    }

    /**
     * 场景2: 测试异步发送普通订单消息
     */
    @Test
    public void testSendOrderAsync() {
        OrderInfo order = new OrderInfo(
                "ORDER002",
                "USER002",
                "MacBook Pro",
                15999.00
        );

        System.out.println("发送异步订单消息: " + order);
        orderMdpClient.sendOrderAsync(order);
        System.out.println("异步消息已提交");
    }

    /**
     * 场景3: 测试发送延迟消息
     */
    @Test
    public void testSendOrderDelayed() {
        OrderInfo order = new OrderInfo(
                "ORDER003",
                "USER003",
                "AirPods Pro",
                1999.00
        );

        DelayLevel delayLevel = DelayLevel.DELAY_10S;
        System.out.println("发送延迟订单消息 (10秒): " + order);
        orderMdpClient.sendOrderDelayed(order, delayLevel);
        System.out.println("延迟消息已提交，将在10秒后被消费");
    }

    /**
     * 场景4: 测试发送带标签的订单消息
     */
    @Test
    public void testSendVipOrder() {
        OrderInfo order = new OrderInfo(
                "ORDER004",
                "VIP_USER001",
                "iPad Pro",
                6999.00
        );

        System.out.println("发送VIP订单消息: " + order);
        orderMdpClient.sendVipOrder(order);
        System.out.println("VIP订单消息已发送");
    }

    /**
     * 场景5: 测试批量发送消息
     */
    @Test
    public void testBatchSendOrders() throws InterruptedException {
        for (int i = 1; i <= 10; i++) {
            OrderInfo order = new OrderInfo(
                    "ORDER_BATCH_" + String.format("%03d", i),
                    "USER_" + String.format("%03d", i),
                    "Product " + i,
                    99.99 * i
            );

            orderMdpClient.sendOrderAsync(order);
            System.out.println("已发送订单 " + i + "/10");
        }

        System.out.println("批量发送完成，等待消费...");
        Thread.sleep(5000);
    }

    /**
     * 场景6: 测试灵活参数转换
     */
    @Test
    public void testFlexibleConversion() {
        OrderInfo order = new OrderInfo(
                "ORDER005",
                "USER005",
                "测试灵活转换",
                100.00
        );

        System.out.println("测试灵活参数转换: " + order);
        orderMdpClient.sendOrderAsync(order);
        System.out.println("消息已发送，框架会自动处理不同包名的参数转换");
    }

    /**
     * 场景7: 测试VIP订单独立消费
     */
    @Test
    public void testSendVipOrderToVipTopic() {
        OrderInfo order = new OrderInfo(
                "VIP_ORDER001",
                "VIP_USER999",
                "Luxury Product",
                99999.00
        );

        System.out.println("发送VIP订单到独立topic: " + order);
        vipOrderMdpClient.sendOrder(order);
        System.out.println("VIP订单消息已发送到 vip_order_service topic");
    }

    /**
     * 场景8: 测试VIP订单异步发送
     */
    @Test
    public void testSendVipOrderAsync() {
        OrderInfo order = new OrderInfo(
                "VIP_ORDER002",
                "VIP_USER888",
                "Premium Service",
                50000.00
        );

        System.out.println("异步发送VIP订单: " + order);
        vipOrderMdpClient.sendOrderAsync(order);
        System.out.println("VIP异步订单已提交");
    }

    /**
     * 场景9: 测试消息幂等性保证（重复发送）
     */
    @Test
    public void testIdempotentMessageConsume() throws InterruptedException {
        OrderInfo order = new OrderInfo(
                "ORDER_IDEMPOTENT_001",
                "USER_IDEMPOTENT",
                "幂等性测试商品",
                9999.00
        );

        System.out.println("=== 测试消息幂等性保证 ===");
        System.out.println("发送相同订单消息3次，验证只处理一次");
        System.out.println();

        // 第一次发送
        System.out.println("[第1次] 发送订单: " + order.getOrderId());
        orderMdpClient.sendOrder(order);
        Thread.sleep(1000);

        // 第二次发送（重复）
        System.out.println("[第2次] 发送相同订单（应该被去重）: " + order.getOrderId());
        orderMdpClient.sendOrder(order);
        Thread.sleep(1000);

        // 第三次发送（重复）
        System.out.println("[第3次] 发送相同订单（应该被去重）: " + order.getOrderId());
        orderMdpClient.sendOrder(order);
        Thread.sleep(1000);

        System.out.println();
        System.out.println("预期结果: 日志中只有1次 '订单处理成功'，其他2次被跳过");
        System.out.println("=== 幂等性测试完成 ===");
    }

    /**
     * 场景10: 测试并发消费幂等性保证
     */
    @Test
    public void testConcurrentIdempotentConsume() throws InterruptedException {
        OrderInfo order = new OrderInfo(
                "ORDER_CONCURRENT_001",
                "USER_CONCURRENT",
                "并发幂等性测试",
                8888.00
        );

        System.out.println("=== 测试并发消费幂等性保证 ===");
        System.out.println("快速发送10条相同订单，验证并发场景下的幂等性");
        System.out.println();

        for (int i = 1; i <= 10; i++) {
            System.out.println("[" + i + "/10] 发送订单: " + order.getOrderId());
            orderMdpClient.sendOrderAsync(order);
            Thread.sleep(50);
        }

        System.out.println();
        System.out.println("等待消费完成...");
        Thread.sleep(5000);

        System.out.println("预期结果: 日志中只有1次 '订单处理成功'，其他9次被去重跳过");
        System.out.println("=== 并发幂等性测试完成 ===");
    }

    /**
     * 场景11: 测试不同订单ID的消息正常消费
     */
    @Test
    public void testDifferentOrdersWithIdempotent() throws InterruptedException {
        System.out.println("=== 测试不同订单的正常消费 ===");
        System.out.println("发送10条不同订单，验证幂等性不影响正常消息");
        System.out.println();

        for (int i = 1; i <= 10; i++) {
            OrderInfo order = new OrderInfo(
                    "ORDER_DIFFERENT_" + String.format("%03d", i),
                    "USER_" + i,
                    "商品 " + i,
                    100.00 * i
            );

            System.out.println("[" + i + "/10] 发送订单: " + order.getOrderId());
            orderMdpClient.sendOrderAsync(order);
        }

        System.out.println();
        System.out.println("等待消费完成...");
        Thread.sleep(5000);

        System.out.println("预期结果: 日志中有10次 '订单处理成功'，每条消息都被处理");
        System.out.println("=== 不同订单消费测试完成 ===");
    }
}
