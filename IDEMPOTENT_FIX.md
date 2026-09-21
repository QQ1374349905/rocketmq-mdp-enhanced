# 幂等性问题修复报告

## 问题诊断

从日志分析，**幂等性拦截没有正常工作**。

### 证据

并发测试中，同一个订单 `ORDER_CONCURRENT_001` 被处理了 **5次**：

```
11:35:43.167 - 第1次：开始处理 → 处理成功
11:35:43.356 - 第2次：开始处理 → 处理成功  ❌ 应该被拦截
11:35:43.420 - 第3次：开始处理 → 处理成功  ❌ 应该被拦截
11:35:43.609 - 第4次：开始处理 → 处理成功  ❌ 应该被拦截
11:35:43.668 - 第5次：开始处理 → 处理成功  ❌ 应该被拦截
```

**预期行为**：第一次处理成功后，后续 4 次应该被幂等性拦截，直接跳过。

---

## 根本原因

### 原因1：IdempotentProcessor 没有注入到 MdpMessageListener

**问题位置**：`MdpServerClassRegister.java:93-94`

```java
// ❌ 错误：没有传入 IdempotentProcessor
MdpMessageListener listener = new MdpMessageListener(
        bean, null, annotation.flexibleConversion());
```

这导致 `MdpMessageListener.idempotentProcessor` 字段为 `null`，虽然代码中有幂等性检查逻辑：

```java
// MdpMessageListener.java:78-84
if (idempotentProcessor != null && idempotentProcessor.needsIdempotent(handleMethod)) {
    boolean canProcess = idempotentProcessor.checkIdempotent(handleMethod, parameter, msg.getMsgId());
    if (!canProcess) {
        log.info("跳过重复消息...");
        continue;
    }
}
```

但因为 `idempotentProcessor == null`，这段代码**永远不会执行**。

### 原因2：缺少 StringRedisTemplate Bean

**问题位置**：`mdp-example/config/RedisConfig.java`

`IdempotentConfig` 需要 `StringRedisTemplate` 来初始化 `RedisIdempotentService`：

```java
// IdempotentConfig.java:66
public IdempotentService redisIdempotentService(StringRedisTemplate stringRedisTemplate) {
    ...
}
```

但 `RedisConfig` 只配置了 `RedisTemplate<String, Object>`，导致无法创建 `RedisIdempotentService`，可能降级到 `MemoryIdempotentService`。

---

## 修复方案

### 修复1：注入 IdempotentProcessor 到 MdpMessageListener

**文件**：`src/main/java/com/gaoji/common/mdp/enhanced/register/MdpServerClassRegister.java`

**改动1 - 导入依赖**：
```java
import com.gaoji.common.mdp.enhanced.idempotent.IdempotentProcessor;
import org.springframework.beans.factory.annotation.Autowired;
```

**改动2 - 添加字段**：
```java
@Autowired(required = false)
private IdempotentProcessor idempotentProcessor;
```

**改动3 - 传入处理器**：
```java
// ✅ 正确：传入 IdempotentProcessor
MdpMessageListener listener = new MdpMessageListener(
        bean, null, annotation.flexibleConversion(), idempotentProcessor);
consumer.registerMessageListener(listener);

// 启动消费者
consumer.start();
consumerMap.put(service, consumer);

log.info("注册增强版MDP消费者成功 - Service: {}, Topic: {}, Group: {}, FlexibleConversion: {}, IdempotentEnabled: {}",
        service, topic, group, annotation.flexibleConversion(), (idempotentProcessor != null));
```

### 修复2：配置 StringRedisTemplate

**文件**：`mdp-example/src/main/java/com/gaoji/common/mdp/example/config/RedisConfig.java`

```java
package com.gaoji.common.mdp.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    /**
     * Redis 连接工厂
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory();
    }

    /**
     * StringRedisTemplate Bean
     * 用于幂等性服务（RedisIdempotentService）
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(redisConnectionFactory);
        return template;
    }

    /**
     * RedisTemplate Bean（通用对象存储）
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);

        // 使用 String 序列化器
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        redisTemplate.setKeySerializer(stringSerializer);
        redisTemplate.setHashKeySerializer(stringSerializer);

        return redisTemplate;
    }
}
```

---

## 验证方法

重新运行测试：

```bash
cd mdp-example
mvn clean test -Dtest=MdpExampleTest#testConcurrentIdempotentConsume
```

### 预期结果

启动日志应该显示：
```
初始化幂等性服务 - 使用 Redis 实现（RedisIdempotentService）
✅ Redis 实现支持分布式部署，适合生产环境
初始化幂等性处理器 - IdempotentProcessor
注册增强版MDP消费者成功 - Service: order.service, Topic: order_service, Group: order_service_consumer_group, FlexibleConversion: true, IdempotentEnabled: true
```

消费日志应该显示：
```
11:35:43.167 [ConsumeMessageThread_1] - 收到异步订单消息: OrderInfo{orderId='ORDER_CONCURRENT_001'...}
11:35:43.167 [ConsumeMessageThread_1] - 开始处理订单: orderId=ORDER_CONCURRENT_001
11:35:43.167 [ConsumeMessageThread_1] - 订单处理成功: orderId=ORDER_CONCURRENT_001

11:35:43.356 [ConsumeMessageThread_2] - 接收消息 - MsgId: xxx
11:35:43.356 [ConsumeMessageThread_2] - 跳过重复消息 - MsgId: xxx, Method: sendOrderAsync
                                      ^^^^^^^^^^^^ 后续消息被拦截

11:35:43.420 [ConsumeMessageThread_3] - 跳过重复消息...
11:35:43.609 [ConsumeMessageThread_4] - 跳过重复消息...
11:35:43.668 [ConsumeMessageThread_5] - 跳过重复消息...
```

**结果**：10 条相同订单只处理 1 次，其他 9 次被幂等性拦截。

---

## 技术细节

### 幂等性流程

1. **消息到达** → `MdpMessageListener.consumeMessage()`
2. **提取业务键** → 使用 SpEL 表达式 `#order.orderId` 提取 `ORDER_CONCURRENT_001`
3. **获取分布式锁** → Redis `SETNX mdp:lock:ORDER_CONCURRENT_001`
4. **检查是否已处理** → Redis `GET mdp:idempotent:ORDER_CONCURRENT_001`
   - 不存在 → 继续处理
   - 已存在 → 返回 `false`，跳过消息
5. **记录处理标识** → Redis `SET mdp:idempotent:ORDER_CONCURRENT_001 = {messageId}` (TTL 24小时)
6. **释放锁** → Lua 脚本原子性释放
7. **调用业务方法** → `OrderMdpServer.sendOrderAsync(order)`
8. **标记成功** → 保持 Redis 记录

### Redis Key 设计

```
mdp:idempotent:ORDER_CONCURRENT_001  -> "7F00000127F818B4AAC2697C23F60020"
                ^^^^^^^^^^^^^^^^^^^^                   ^^^^^^^^^^^^^^^^^^^^^
                    业务唯一键                            RocketMQ MessageId
                    
TTL: 86400秒（24小时）
```

### 并发安全保证

- **分布式锁**：`mdp:lock:{businessKey}`，防止并发竞态
- **锁超时**：默认 10 秒，防止死锁
- **Lua 脚本释放锁**：保证原子性，只有持锁线程才能释放

---

## 问题总结

| 问题 | 原因 | 影响 | 修复 |
|------|------|------|------|
| 幂等性完全失效 | `IdempotentProcessor` 未注入 | 重复消息全部被处理 | 在 `MdpServerClassRegister` 中注入并传递 |
| Redis 实现未启用 | 缺少 `StringRedisTemplate` Bean | 可能降级到内存实现 | `RedisConfig` 中添加 `stringRedisTemplate()` |

修复后，幂等性功能将正常工作，重复消息会被自动拦截。
