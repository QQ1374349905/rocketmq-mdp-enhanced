# MDP 框架幂等性保证功能

## 功能概述

本框架提供了完整的消息幂等性保证机制，确保在以下场景下消息只被处理一次：

### 解决的问题

❌ **问题1：多生产者重复发送**
- 多个实例可能发送相同的订单
- 业务重试导致重复发送
- **后果：** 重复创建订单、重复扣款

❌ **问题2：消费失败重试**
- RocketMQ 消费失败会自动重试
- 重试时业务逻辑可能已执行
- **后果：** 重复扣库存、重复积分

❌ **问题3：RocketMQ at-least-once**
- RocketMQ 保证至少投递一次
- 极端情况下消息可能重复投递
- **后果：** 同一消息被处理多次

❌ **问题4：多消费者并发消费**
- 集群模式下多个消费线程并发处理
- 可能同时处理相同的消息
- **后果：** 并发重复处理

✅ **解决方案：基于业务唯一键的幂等性保证**

## 核心特性

### 1. 双重实现，自动选择

- **Redis 实现**（RedisIdempotentService）
  - ✅ 支持分布式部署
  - ✅ 持久化，重启不丢失
  - ✅ 高性能（Redis 内存操作）
  - ✅ 自动过期（TTL）
  - 📦 需要配置 Redis

- **内存实现**（MemoryIdempotentService）
  - ✅ 无需外部依赖
  - ✅ 适合单机/测试
  - ⚠️ 不支持分布式
  - ⚠️ 重启丢失数据

**自动选择逻辑：**
```
if (存在 RedisTemplate Bean) {
    使用 RedisIdempotentService  // 生产环境推荐
} else {
    使用 MemoryIdempotentService  // 开发/测试环境
}
```

### 2. 灵活的业务键提取

**方式1：使用 SpEL 表达式（推荐）**

使用 SpEL 表达式从消息参数中提取业务唯一键：

```java
@Idempotent(keyExpression = "#order.orderId")  // 订单ID
public void sendOrder(OrderInfo order) { ... }

@Idempotent(keyExpression = "#user.userId")    // 用户ID
public void updateUser(UserInfo user) { ... }

@Idempotent(keyExpression = "#order.orderId + '_' + #order.userId")  // 组合键
public void processOrder(OrderInfo order) { ... }
```

**方式2：使用参数 MD5（默认）**

不指定 `keyExpression` 时，框架会自动使用参数的 MD5 值：

```java
@Idempotent  // 使用参数对象的 MD5 作为业务键
public void sendNotification(OrderInfo order) { ... }
```

**MD5 计算逻辑：**
- 基本类型（String、Number、Boolean）→ 直接 `toString()`
- 对象类型 → JSON 序列化后计算 MD5
- 相同内容的对象生成相同的 MD5（避免内存地址误判）

**适用场景：**
- ✅ 消息没有明确的业务唯一键
- ✅ 完全基于消息内容去重
- ⚠️ 对象结构变化会导致 MD5 变化

### 3. 分布式锁防并发

使用分布式锁保证并发安全：

```java
@Idempotent(
    keyExpression = "#order.orderId",
    lockTimeout = 10  // 分布式锁超时时间（秒）
)
```

**锁机制：**
- Redis 实现：基于 SETNX + Lua 脚本
- 内存实现：基于 ReentrantLock
- 确保同一业务键同一时刻只有一个线程处理

### 4. 可配置的过期时间

```java
@Idempotent(
    keyExpression = "#order.orderId",
    timeout = 86400  // 去重记录保留时间（秒）
)
```

**常见配置：**
- 订单：86400（24小时）
- 支付：259200（3天）
- 日志：3600（1小时）

### 5. 灵活的重复策略

```java
@Idempotent(
    keyExpression = "#order.orderId",
    duplicateStrategy = DuplicateStrategy.SKIP  // 跳过重复消息（默认）
)

@Idempotent(
    keyExpression = "#order.orderId",
    duplicateStrategy = DuplicateStrategy.EXCEPTION  // 抛异常，触发重试
)
```

## 使用示例

### 1. 基本使用（SpEL 表达式）

```java
@Component
@MdpServer(service = "order.service")
public class OrderMdpServer {

    // 添加 @Idempotent 注解即可
    @Idempotent(keyExpression = "#order.orderId", timeout = 86400)
    public void sendOrder(OrderInfo order) {
        logger.info("处理订单: {}", order.getOrderId());
        
        // 业务逻辑：扣库存、创建订单、扣款
        processOrder(order);
        
        // 框架保证：相同 orderId 的消息只会执行一次
    }
}
```

### 2. 使用默认 MD5 模式

```java
@Component
@MdpServer(service = "notification.service")
public class NotificationMdpServer {

    // 不指定 keyExpression，自动使用参数的 MD5
    @Idempotent
    public void sendNotification(NotificationInfo notification) {
        logger.info("发送通知: {}", notification);
        
        // 框架会：
        // 1. 将 notification 对象序列化为 JSON
        // 2. 计算 JSON 的 MD5 值作为业务键
        // 3. 基于 MD5 进行幂等性去重
        
        sendEmail(notification);
    }
}
```

### 3. 配置 Redis（推荐）

**application.yml：**
```yaml
spring:
  redis:
    host: localhost
    port: 6379
    password: your_password
    database: 0
    timeout: 5000ms
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
```

**启动日志：**
```
初始化幂等性服务 - 使用 Redis 实现（RedisIdempotentService）
Redis 实现支持分布式部署，适合生产环境
```

### 4. 不配置 Redis（测试环境）

不配置 Redis，框架自动使用内存实现：

**启动日志：**
```
初始化幂等性服务 - 使用内存实现（MemoryIdempotentService）
⚠️  内存实现不支持分布式部署，仅适合单机/测试环境
⚠️  生产环境建议配置 Redis 以使用 RedisIdempotentService
```

## 工作原理

### Redis 实现流程

```
1. 消息到达 → MdpMessageListener
2. 检查方法是否有 @Idempotent 注解
3. 提取业务键：orderId = "ORDER001"
4. 尝试获取分布式锁：mdp:lock:ORDER001
   ├─ 获取成功 → 继续
   └─ 获取失败 → 返回 false（并发冲突）
5. 检查去重记录：mdp:idempotent:ORDER001
   ├─ 不存在 → 记录并继续处理
   └─ 已存在 → 返回 false（重复消息）
6. 执行业务逻辑
7. 标记处理成功
8. 释放分布式锁（Lua 脚本保证原子性）
```

### Redis Key 设计

```
去重记录：mdp:idempotent:{businessKey} -> {messageId}
          └─ TTL: timeout 秒

分布式锁：mdp:lock:{businessKey} -> {lockValue}
          └─ TTL: lockTimeout 秒
```

### 消息状态转换

```
┌─────────┐
│ 新消息  │
└────┬────┘
     │
     ▼
┌─────────────┐
│ 获取分布式锁│
└────┬────┬───┘
     │    │
  成功│    │失败（并发冲突）
     │    └──────────────┐
     ▼                   ▼
┌─────────────┐    ┌──────────┐
│ 检查去重记录│    │ 跳过消息 │
└────┬────┬───┘    └──────────┘
     │    │
  不存在  │存在（重复消息）
     │    └──────────────┐
     ▼                   ▼
┌─────────────┐    ┌──────────┐
│ 记录去重标识│    │ 跳过消息 │
└────┬────────┘    └──────────┘
     │
     ▼
┌─────────────┐
│ 执行业务逻辑│
└────┬────┬───┘
     │    │
  成功│    │失败
     │    └──────────────┐
     ▼                   ▼
┌─────────────┐    ┌──────────┐
│ 标记成功    │    │ 删除记录 │
│ 释放锁      │    │ 允许重试 │
└─────────────┘    └──────────┘
```

## 测试场景

### 场景1：重复消息去重

```java
@Test
public void testIdempotentMessageConsume() throws InterruptedException {
    OrderInfo order = new OrderInfo("ORDER001", "USER001", "商品", 100.0);
    
    // 发送3次相同订单
    orderMdpClient.sendOrder(order);  // ✅ 第1次：处理成功
    orderMdpClient.sendOrder(order);  // ⏭️  第2次：跳过（重复）
    orderMdpClient.sendOrder(order);  // ⏭️  第3次：跳过（重复）
    
    // 预期：只有1次 "订单处理成功"
}
```

### 场景2：并发消息去重

```java
@Test
public void testConcurrentIdempotentConsume() throws InterruptedException {
    OrderInfo order = new OrderInfo("ORDER001", "USER001", "商品", 100.0);
    
    // 快速发送10条相同订单（模拟并发）
    for (int i = 0; i < 10; i++) {
        orderMdpClient.sendOrderAsync(order);
    }
    
    // 预期：只有1次 "订单处理成功"，其他9次被去重
}
```

### 场景3：不同订单正常消费

```java
@Test
public void testDifferentOrdersWithIdempotent() throws InterruptedException {
    // 发送10条不同订单
    for (int i = 1; i <= 10; i++) {
        OrderInfo order = new OrderInfo("ORDER" + i, "USER" + i, "商品", 100.0);
        orderMdpClient.sendOrderAsync(order);
    }
    
    // 预期：10次 "订单处理成功"（幂等性不影响正常消息）
}
```

## 监控与运维

### Redis 监控

```bash
# 查看去重记录数量
redis-cli --scan --pattern "mdp:idempotent:*" | wc -l

# 查看具体记录
redis-cli get mdp:idempotent:ORDER001

# 查看过期时间
redis-cli ttl mdp:idempotent:ORDER001

# 清理测试数据
redis-cli --scan --pattern "mdp:idempotent:*" | xargs redis-cli del
```

### 日志监控

**关键日志：**
```
检测到重复消息 - businessKey: ORDER001, 原始messageId: xxx, 当前messageId: yyy
获取分布式锁失败，可能存在并发消费 - businessKey: ORDER001
```

**建议监控指标：**
- 重复消息次数（反映消息重复投递情况）
- 锁冲突次数（反映并发冲突情况）
- 去重记录数量（反映消息处理量）

## 性能与容量

### Redis 实现性能

- **QPS**：单 Redis 实例可支持 10万+ QPS
- **延迟**：平均 1-2ms（网络 + Redis 操作）
- **内存占用**：每条记录约 100 bytes

**容量估算：**
```
1百万条记录 ≈ 100MB 内存
1千万条记录 ≈ 1GB 内存
```

### 性能优化建议

1. ✅ 使用 Redis 集群（提高吞吐量）
2. ✅ 设置合理的 timeout（控制内存占用）
3. ✅ 使用 Redis Pipeline（减少网络往返）
4. ✅ 监控 Redis 性能（慢查询、内存）

## 故障处理

### Redis 不可用

框架不会自动降级到内存实现，会直接返回 false（幂等性检查失败）。

**处理方式：**
- 消息返回 `RECONSUME_LATER`，触发重试
- 等待 Redis 恢复后重新处理

**建议：**
- 使用 Redis 集群/哨兵（高可用）
- 监控 Redis 状态
- 设置合理的超时时间

### 消费失败重试

消费失败时，框架会删除去重记录，允许重试：

```java
try {
    // 业务逻辑
    processOrder(order);
    idempotentService.markSuccess(businessKey, messageId);  // 标记成功
} catch (Exception e) {
    idempotentService.markFailed(businessKey, messageId);   // 删除记录
    return RECONSUME_LATER;  // 触发重试
}
```

## 最佳实践

### 1. 选择合适的业务键

✅ **好的业务键：**
- 订单ID：`orderId`
- 用户ID：`userId`
- 交易流水号：`transactionId`

❌ **不好的业务键：**
- 时间戳（每次都不同）
- 随机数（无法去重）
- 对象哈希（不稳定）

### 2. 设置合理的过期时间

- 订单：24小时（一般当天处理完毕）
- 支付：3天（对账周期）
- 日志：1小时（实时性要求高）

### 3. 生产环境配置

```yaml
spring:
  redis:
    # 使用 Redis 集群
    cluster:
      nodes:
        - 192.168.1.1:6379
        - 192.168.1.2:6379
        - 192.168.1.3:6379
    # 连接池配置
    lettuce:
      pool:
        max-active: 16
        max-idle: 8
        min-idle: 4
```

### 4. 监控告警

- Redis 可用性告警
- 重复消息次数告警（异常增长）
- 锁冲突次数告警（并发过高）
- Redis 内存使用告警（容量不足）

## 架构演进

### V1: 内存实现（单机）
```
单实例 → 内存去重 → ✅ 简单，但不支持分布式
```

### V2: Redis 实现（分布式）
```
多实例 → Redis 共享状态 → ✅ 支持分布式，高可用
```

### V3: 数据库实现（持久化）
```
多实例 → 数据库去重表 → ✅ 持久化，可追溯历史
```

## 总结

幂等性保证是分布式系统的基础能力。本框架通过：

✅ **灵活的实现选择** - Redis/内存自动切换
✅ **简单的使用方式** - 一个注解搞定
✅ **完善的并发控制** - 分布式锁保证安全
✅ **合理的过期策略** - 自动清理，控制容量
✅ **生产级的可靠性** - Redis 集群，高可用

确保消息在任何情况下都只被处理一次，解决了重复消费的核心问题。

明天测试愉快！🎉
