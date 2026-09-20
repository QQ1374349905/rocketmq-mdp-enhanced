package com.gaoji.common.mdp.enhanced.example;

import com.gaoji.common.mdp.enhanced.annotation.EnableMdp;
import com.gaoji.common.mdp.enhanced.enums.DelayLevel;
import com.gaoji.common.mdp.enhanced.example.client.OrderMdpClient;
import com.gaoji.common.mdp.enhanced.example.client.VipOrderMdpClient;
import com.gaoji.common.mdp.enhanced.example.domain.OrderInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * MDP框架使用示例测试
 *
 * 演示如何使用 @MdpClient 和 @MdpServer 进行消息发送和接收
 *
 * 测试场景说明:
 * 1. 普通订单场景：使用 OrderMdpClient 发送，OrderMdpServer 消费（topic: order_service）
 * 2. VIP订单场景：使用 VipOrderMdpClient 发送，VipOrderMdpServer 消费（topic: vip_order_service）
 * 3. 同步/异步发送场景：测试消息发送的同步和异步模式
 * 4. 延迟消息场景：测试延迟消息投递
 * 5. 带标签消息场景：测试消息标签过滤
 * 6. 批量发送场景：测试高并发消息发送
 * 7. 灵活参数转换场景：测试不同包名的同名类自动转换
 *
 * 架构设计原则:
 * - 一个 topic 对应一个消费者类，避免消息路由冲突
 * - 客户端方法名必须与服务端方法名一致
 * - 框架根据消息中的 methodName 动态路由到对应的服务端方法
 */
@RunWith(SpringRunner.class)
@EnableMdp
@SpringBootTest(classes = MdpExampleApplication.class)
public class MdpExampleTest {

    @Autowired
    private OrderMdpClient orderMdpClient;

    @Autowired
    private VipOrderMdpClient vipOrderMdpClient;

    /**
     * 场景1: 测试同步发送普通订单消息
     *
     * 流程:
     * 1. 客户端调用 OrderMdpClient.sendOrder()
     * 2. 框架将方法名 "sendOrder" 包装到 MdpMessage 中
     * 3. 消息发送到 topic: order_service
     * 4. OrderMdpServer 接收消息，根据 methodName 路由到 sendOrder() 方法
     * 5. 同步等待发送结果返回
     *
     * 注意: isSync=true 会阻塞等待发送结果
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
     *
     * 流程:
     * 1. 客户端调用 OrderMdpClient.sendOrderAsync()
     * 2. 框架将方法名 "sendOrderAsync" 包装到消息中
     * 3. 消息发送到 topic: order_service
     * 4. OrderMdpServer 根据 methodName 路由到 sendOrderAsync() 方法
     * 5. 不等待发送结果，立即返回
     *
     * 注意: isSync=false 不阻塞，适合高吞吐场景
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
     * 场景3: 测试发送延迟消息
     *
     * 流程:
     * 1. 客户端调用 OrderMdpClient.sendOrderDelayed()
     * 2. 指定延迟级别 DelayLevel.SECONDS_10
     * 3. 消息发送到 topic: order_service，设置延迟级别
     * 4. RocketMQ 延迟投递，10秒后才会被消费
     * 5. OrderMdpServer 根据 methodName 路由到 sendOrderDelayed() 方法
     *
     * 注意: RocketMQ 只支持固定的18个延迟级别，不支持任意时间延迟
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
     * 场景4: 测试发送带标签的订单消息
     *
     * 流程:
     * 1. 客户端调用 OrderMdpClient.sendVipOrder()
     * 2. @MdpMethod 配置 tags="VIP_ORDER"
     * 3. 消息发送到 topic: order_service，带 tags
     * 4. 消费者可以通过 tags 过滤消息（当前未实现过滤）
     * 5. OrderMdpServer 根据 methodName 路由到 sendVipOrder() 方法
     *
     * 注意: 当前实现未使用 tags 过滤，仅作为消息标记
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
     *
     * 流程:
     * 1. 循环发送10条订单消息
     * 2. 使用异步发送提高吞吐量
     * 3. 所有消息发送到 topic: order_service
     * 4. OrderMdpServer 并发消费消息
     *
     * 注意:
     * - 异步发送不保证顺序
     * - RocketMQ 默认使用并发消费模式
     * - 同一条消息可能被重复消费（at least once）
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
     * 场景6: 测试灵活参数转换
     *
     * 流程:
     * 1. 客户端发送 OrderInfo 对象
     * 2. 框架序列化为 JSON 并记录原始类名
     * 3. 服务端接收消息，尝试反序列化为目标类型
     * 4. 如果直接反序列化失败，使用 ParameterConverter 进行灵活转换
     * 5. 支持不同包名的同名类自动转换
     *
     * 注意:
     * - 需要在 @MdpServer 中开启 flexibleConversion=true
     * - 基于字段名和类型进行转换，字段名必须一致
     * - 适用于微服务间模型定义不一致的场景
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
     * 场景7: 测试VIP订单独立消费（新增）
     *
     * 流程:
     * 1. 客户端调用 VipOrderMdpClient.sendOrder()
     * 2. 消息发送到独立的 topic: vip_order_service
     * 3. VipOrderMdpServer 接收消息，根据 methodName 路由到 sendOrder() 方法
     * 4. 检查是否为VIP用户（userId 以 "VIP_" 开头）
     * 5. 执行VIP专属逻辑：优先配送、赠送积分、专属客服
     *
     * 架构设计:
     * - OrderMdpClient → order_service → OrderMdpServer（普通订单）
     * - VipOrderMdpClient → vip_order_service → VipOrderMdpServer（VIP订单）
     * - 两个 topic 完全隔离，避免消息路由冲突
     *
     * 关键问题:
     * ❌ 错误设计: 多个消费者订阅同一个 topic，消息随机分配可能路由到没有对应方法的消费者
     * ✅ 正确设计: 一个 topic 对应一个消费者类，确保消息必然路由到正确的消费者
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
}
