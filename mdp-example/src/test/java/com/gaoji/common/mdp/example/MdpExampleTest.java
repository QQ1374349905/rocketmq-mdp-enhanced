package com.gaoji.common.mdp.example;

import com.gaoji.common.mdp.enhanced.enums.DelayLevel;
import com.gaoji.common.mdp.example.client.OrderMdpClient;
import com.gaoji.common.mdp.example.client.VipOrderMdpClient;
import com.gaoji.common.mdp.example.domain.OrderInfo;
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
     * <p>
     * 流程:
     * 1. 客户端调用 OrderMdpClient.sendOrder()
     * 2. 框架将方法名 "sendOrder" 包装到 MdpMessage 中
     * 3. 消息发送到 topic: order_service
     * 4. OrderMdpServer 接收消息，根据 methodName 路由到 sendOrder() 方法
     * 5. 同步等待发送结果返回
     * <p>
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
     * <p>
     * 流程:
     * 1. 客户端调用 OrderMdpClient.sendOrderAsync()
     * 2. 框架将方法名 "sendOrderAsync" 包装到消息中
     * 3. 消息发送到 topic: order_service
     * 4. OrderMdpServer 根据 methodName 路由到 sendOrderAsync() 方法
     * 5. 不等待发送结果，立即返回
     * <p>
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
     * <p>
     * 流程:
     * 1. 客户端调用 OrderMdpClient.sendOrderDelayed()
     * 2. 指定延迟级别 DelayLevel.SECONDS_10
     * 3. 消息发送到 topic: order_service，设置延迟级别
     * 4. RocketMQ 延迟投递，10秒后才会被消费
     * 5. OrderMdpServer 根据 methodName 路由到 sendOrderDelayed() 方法
     * <p>
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
     * <p>
     * 流程:
     * 1. 客户端调用 OrderMdpClient.sendVipOrder()
     * 2. @MdpMethod 配置 tags="VIP_ORDER"
     * 3. 消息发送到 topic: order_service，带 tags
     * 4. 消费者可以通过 tags 过滤消息（当前未实现过滤）
     * 5. OrderMdpServer 根据 methodName 路由到 sendVipOrder() 方法
     * <p>
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
     * <p>
     * 流程:
     * 1. 循环发送10条订单消息
     * 2. 使用异步发送提高吞吐量
     * 3. 所有消息发送到 topic: order_service
     * 4. OrderMdpServer 并发消费消息
     * <p>
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
     * <p>
     * 流程:
     * 1. 客户端发送 OrderInfo 对象
     * 2. 框架序列化为 JSON 并记录原始类名
     * 3. 服务端接收消息，尝试反序列化为目标类型
     * 4. 如果直接反序列化失败，使用 ParameterConverter 进行灵活转换
     * 5. 支持不同包名的同名类自动转换
     * <p>
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
     * <p>
     * 流程:
     * 1. 客户端调用 VipOrderMdpClient.sendOrder()
     * 2. 消息发送到独立的 topic: vip_order_service
     * 3. VipOrderMdpServer 接收消息，根据 methodName 路由到 sendOrder() 方法
     * 4. 检查是否为VIP用户（userId 以 "VIP_" 开头）
     * 5. 执行VIP专属逻辑：优先配送、赠送积分、专属客服
     * <p>
     * 架构设计:
     * - OrderMdpClient → order_service → OrderMdpServer（普通订单）
     * - VipOrderMdpClient → vip_order_service → VipOrderMdpServer（VIP订单）
     * - 两个 topic 完全隔离，避免消息路由冲突
     * <p>
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

    /**
     * 场景9: 测试消息幂等性保证（重复发送）
     * <p>
     * 流程:
     * 1. 发送同一个订单消息3次（相同的orderId）
     * 2. 第一次消费成功，记录去重标识
     * 3. 第二次和第三次检测到重复，直接跳过
     * 4. 最终订单只被处理一次
     * <p>
     * 幂等性保证机制:
     * - @Idempotent 注解标注需要幂等性保证的方法
     * - keyExpression = "#order.orderId" 从参数中提取业务唯一键
     * - IdempotentService 记录已处理的消息
     * - 重复消息直接跳过，返回消费成功
     * <p>
     * 关键问题场景:
     * ❌ 问题: 多生产者发送相同订单 → 重复创建订单
     * ❌ 问题: 消费失败重试 → 重复扣库存/扣款
     * ❌ 问题: RocketMQ at-least-once 保证 → 消息可能重复投递
     * ✅ 解决: 基于业务唯一键（订单ID）的幂等性保证
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
     * <p>
     * 流程:
     * 1. 快速连续发送10条相同订单消息
     * 2. RocketMQ 可能并发投递给多个消费线程
     * 3. 分布式锁保证只有一个线程能处理
     * 4. 其他线程检测到重复，直接跳过
     * <p>
     * 并发场景:
     * - 多个消费线程同时处理相同的 orderId
     * - 分布式锁（lockTimeout=10s）防止竞态条件
     * - 第一个获得锁的线程处理消息
     * - 其他线程等待后检测到已处理，跳过
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
            Thread.sleep(50); // 快速发送，模拟并发
        }

        System.out.println();
        System.out.println("等待消费完成...");
        Thread.sleep(5000);

        System.out.println("预期结果: 日志中只有1次 '订单处理成功'，其他9次被去重跳过");
        System.out.println("=== 并发幂等性测试完成 ===");
    }

    /**
     * 场景11: 测试不同订单ID的消息正常消费
     * <p>
     * 流程:
     * 1. 发送10条不同订单ID的消息
     * 2. 每条消息都有唯一的 orderId
     * 3. 所有消息都应该被正常处理
     * 4. 验证幂等性不影响正常消息
     * <p>
     * 验证点:
     * - 幂等性只对相同业务键的消息去重
     * - 不同业务键的消息独立处理
     * - 不会误杀正常消息
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
