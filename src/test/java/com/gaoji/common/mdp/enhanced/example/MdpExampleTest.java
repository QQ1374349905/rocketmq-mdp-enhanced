package com.gaoji.common.mdp.enhanced.example;

import com.gaoji.common.mdp.enhanced.annotation.EnableMdp;
import com.gaoji.common.mdp.enhanced.enums.DelayLevel;
import com.gaoji.common.mdp.enhanced.example.client.OrderMdpClient;
import com.gaoji.common.mdp.enhanced.example.domain.OrderInfo;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * MDP框架使用示例测试
 *
 * 演示如何使用 @MdpClient 和 @MdpServer 进行消息发送和接收
 */
@EnableMdp
@SpringBootTest(classes = MdpExampleApplication.class)
public class MdpExampleTest {

    @Autowired
    private OrderMdpClient orderMdpClient;

    /**
     * 测试同步发送订单消息
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
     * 测试异步发送订单消息
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

        // 异步发送不会等待，可以继续执行其他操作
    }

    /**
     * 测试发送延迟消息
     */
    @Test
    public void testSendOrderDelayed() {
        OrderInfo order = new OrderInfo(
            "ORDER003",
            "USER003",
            "AirPods Pro",
            1999.00
        );

        DelayLevel delayLevel = DelayLevel.SECONDS_10;
        System.out.println("发送延迟订单消息 (" + delayLevel.getDescription() + "): " + order);
        orderMdpClient.sendOrderDelayed(order, delayLevel);
        System.out.println("延迟消息已提交，将在" + delayLevel.getDescription() + "后被消费");
    }

    /**
     * 测试发送VIP订单消息（带标签）
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
     * 测试批量发送消息
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
        Thread.sleep(5000); // 等待消费
    }

    /**
     * 测试灵活参数转换
     * 即使消费端和生产端的OrderInfo在不同包名，也能自动转换
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
}
