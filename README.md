# MDP (Message-Driven Programming) 框架

## 概述

`rocketmq-mdp-enhanced` 是一个基于 RocketMQ 的消息驱动编程框架，通过注解和动态代理大幅简化 RocketMQ 的使用。开发者无需编写 RocketMQ 的模板代码，像调用本地方法一样发送和接收消息，专注于业务逻辑。

## 核心优势

- **接口编程模式** - 定义接口即可发送消息，框架自动生成实现
- **零配置自动化** - 自动生成 Topic 和 Consumer Group，无需手动管理
- **方法名自动路由** - 消费者方法名与生产者方法名匹配，自动路由消息
- **延迟消息支持** - 18个延迟级别（1秒到2小时），使用类型安全的枚举
- **灵活参数转换** - 自动处理不同包名、不同类之间的参数转换
- **内置幂等性保证** - 支持 Redis/内存双实现，防止消息重复消费，零配置或字段路径模式

## 原生 RocketMQ vs MDP 框架

### 生产者对比

#### 原生 RocketMQ（繁琐）

```java
@Service
public class OrderService {
    
    @Autowired
    private RocketMQTemplate rocketMQTemplate;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    // 发送普通消息
    public void sendOrder(OrderInfo order) {
        try {
            // 手动序列化
            String json = objectMapper.writeValueAsString(order);
            // 手动指定 topic
            rocketMQTemplate.convertAndSend("order_topic", json);
        } catch (Exception e) {
            log.error("发送消息失败", e);
        }
    }
    
    // 发送延迟消息
    public void sendDelayedOrder(OrderInfo order) {
        try {
            String json = objectMapper.writeValueAsString(order);
            Message<String> message = MessageBuilder
                .withPayload(json)
                .build();
            // 手动设置延迟级别（魔法数字）
            rocketMQTemplate.syncSend(
                "order_topic",
                message,
                3000,
                14  // 什么意思？10分钟？需要查文档
            );
        } catch (Exception e) {
            log.error("发送延迟消息失败", e);
        }
    }
}
```

#### MDP 框架（简洁）

```java
// 1. 定义接口
@MdpClient(service = "order.service")
public interface OrderMdpClient {
    
    @MdpMethod(isSync = false)
    void sendOrder(OrderInfo order);
    
    @MdpMethod(isSync = false, delayLevel = DelayLevel.MINUTES_10)
    void sendOrderDelayed(OrderInfo order);
}

// 2. 直接使用
@Service
public class OrderService {
    
    @Autowired
    private OrderMdpClient orderMdpClient;
    
    public void createOrder(OrderInfo order) {
        // 像调用本地方法一样，无需序列化
        orderMdpClient.sendOrder(order);
    }
    
    public void createDelayedOrder(OrderInfo order) {
        // 延迟10分钟发送
        orderMdpClient.sendOrderDelayed(order);
    }
}
```

### 消费者对比

#### 原生 RocketMQ（繁琐）

```java
@Service
@RocketMQMessageListener(
    topic = "order_topic",
    consumerGroup = "order_consumer_group",
    messageModel = MessageModel.CLUSTERING
)
public class OrderConsumer implements RocketMQListener<String> {
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Override
    public void onMessage(String message) {
        try {
            // 手动反序列化
            OrderInfo order = objectMapper.readValue(message, OrderInfo.class);
            
            // 手动实现幂等性检查
            String key = "order:" + order.getOrderId();
            Boolean exists = redisTemplate.hasKey(key);
            if (Boolean.TRUE.equals(exists)) {
                log.warn("重复消息，跳过: {}", order.getOrderId());
                return;
            }
            
            // 设置分布式锁，防止并发
            Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(key + ":lock", "1", 10, TimeUnit.SECONDS);
            if (Boolean.FALSE.equals(locked)) {
                throw new RuntimeException("获取锁失败");
            }
            
            try {
                // 业务逻辑
                processOrder(order);
                
                // 标记已处理
                redisTemplate.opsForValue().set(key, "1", 24, TimeUnit.HOURS);
            } finally {
                // 释放锁
                redisTemplate.delete(key + ":lock");
            }
            
        } catch (Exception e) {
            log.error("消息处理失败", e);
            throw new RuntimeException(e);
        }
    }
    
    private void processOrder(OrderInfo order) {
        // 业务逻辑
    }
}
```

#### MDP 框架（简洁）

```java
@Component
@MdpServer(service = "order.service")
public class OrderMdpServer {
    
    // 自动反序列化、自动幂等性保证、自动分布式锁
    @Idempotent(key = "orderId", timeout = 86400)
    public void sendOrder(OrderInfo order) {
        // 直接写业务逻辑，框架处理所有其他事情
        processOrder(order);
    }
    
    @Idempotent(key = "orderId", timeout = 86400)
    public void sendOrderDelayed(OrderInfo order) {
        processOrder(order);
    }
    
    private void processOrder(OrderInfo order) {
        // 业务逻辑
    }
}
```

### 对比总结

| 对比项 | 原生 RocketMQ | MDP 框架 |
|--------|--------------|----------|
| **生产者定义** | 注入 RocketMQTemplate | 定义接口 + @MdpClient，自动生成实现 |
| **消费者定义** | 实现 RocketMQListener 接口 | 定义方法 + @MdpServer，方法名自动路由 |
| **消息发送** | 手动序列化 + convertAndSend | 直接调用接口方法 |
| **消息接收** | 手动反序列化 | 自动转换为对象 |
| **Topic 管理** | 手动指定字符串常量 | 基于 service 自动生成 |
| **Group 管理** | 手动指定字符串常量 | 自动生成（{service}_consumer_group） |
| **延迟消息** | 手动构造 Message + 魔法数字 | DelayLevel 枚举，类型安全 |
| **幂等性保证** | 手动实现 Redis 去重逻辑 | @Idempotent 注解自动处理 |
| **分布式锁** | 手动实现 Redis 锁 | 框架自动处理 |
| **异常处理** | 需要 try-catch | 框架自动处理 |
| **类型安全** | 字符串操作，运行时错误 | 编译时检查 |
| **代码量** | 大量模板代码 | 只需业务逻辑 |
| **学习成本** | 需要深入了解 RocketMQ | 会用接口就会用 |

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.rocketmq</groupId>
    <artifactId>rocketmq-mdp-enhanced</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置 RocketMQ

```yaml
# application.yml
rocketmq:
  name-server: 127.0.0.1:9876

# 可选：配置 Redis 启用分布式幂等性
spring:
  redis:
    host: localhost
    port: 6379
```

### 3. 启用 MDP 框架

```java
@SpringBootApplication
@EnableMdp  // 启用 MDP 框架
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 4. 定义生产者接口

```java
import com.rocketmq.mdp.enhanced.annotation.MdpClient;
import com.rocketmq.mdp.enhanced.annotation.MdpMethod;
import com.rocketmq.mdp.enhanced.enums.DelayLevel;

/**
 * 订单消息生产者
 * Topic 自动生成为: "order.service"
 */
@MdpClient(service = "order.service")
public interface OrderMdpClient {

    /**
     * 同步发送订单消息
     */
    @MdpMethod(isSync = true)
    void sendOrder(OrderInfo order);

    /**
     * 异步发送订单消息
     */
    @MdpMethod(isSync = false)
    void sendOrderAsync(OrderInfo order);

    /**
     * 发送延迟订单消息（延迟10秒）
     * @param order 订单信息
     */
    @MdpMethod(isSync = false, delayLevel = DelayLevel.SECONDS_10)
    void sendOrderDelayed(OrderInfo order);

    /**
     * 发送带标签的 VIP 订单消息
     */
    @MdpMethod(isSync = false, tags = "VIP_ORDER")
    void sendVipOrder(OrderInfo order);
}
```

### 5. 使用生产者发送消息

```java
@Service
public class OrderService {

    @Autowired
    private OrderMdpClient orderMdpClient;

    public void createOrder(OrderInfo order) {
        // 同步发送
        orderMdpClient.sendOrder(order);
    }

    public void createOrderAsync(OrderInfo order) {
        // 异步发送
        orderMdpClient.sendOrderAsync(order);
    }

    public void scheduleOrderCancel(OrderInfo order) {
        // 10分钟后取消订单
        orderMdpClient.sendOrderDelayed(order, DelayLevel.DELAY_10M);
    }

    public void createVipOrder(OrderInfo order) {
        // 发送 VIP 订单
        orderMdpClient.sendVipOrder(order);
    }
}
```

### 6. 定义消费者

```java
import com.rocketmq.mdp.enhanced.annotation.MdpServer;
import com.rocketmq.mdp.enhanced.annotation.Idempotent;
import org.springframework.stereotype.Component;

/**
 * 订单消息消费者
 * Topic: "order.service"
 * Group: "order.service_consumer_group"（自动生成）
 */
@Component
@MdpServer(service = "order.service")
public class OrderMdpServer {

    /**
     * 处理同步订单消息
     * 方法名必须与生产者接口方法名一致：sendOrder
     * 
     * @Idempotent 注解保证幂等性：
     * - key = "orderId" 表示使用 order.getOrderId() 作为业务唯一键
     * - timeout = 86400 表示去重记录保留 24 小时
     */
    @Idempotent(key = "orderId", timeout = 86400)
    public void sendOrder(OrderInfo order) {
        System.out.println("收到订单: " + order.getOrderId());
        // 业务逻辑
    }

    /**
     * 处理异步订单消息
     */
    @Idempotent(key = "orderId", timeout = 86400)
    public void sendOrderAsync(OrderInfo order) {
        System.out.println("收到异步订单: " + order.getOrderId());
        // 业务逻辑
    }

    /**
     * 处理延迟订单消息
     */
    @Idempotent(key = "orderId", timeout = 86400)
    public void sendOrderDelayed(OrderInfo order) {
        System.out.println("收到延迟订单: " + order.getOrderId());
        // 业务逻辑：取消订单
    }

    /**
     * 处理 VIP 订单消息
     */
    @Idempotent(key = "orderId", timeout = 86400)
    public void sendVipOrder(OrderInfo order) {
        System.out.println("收到 VIP 订单: " + order.getOrderId());
        // 业务逻辑
    }
}
```

## 核心功能详解

### 1. 自动化配置

#### Topic 和 Group 自动生成

```java
// service = "order.service" 会自动生成：
// - Topic: "order.service"
// - Consumer Group: "order.service_consumer_group"

@MdpClient(service = "order.service")
public interface OrderMdpClient { }

@MdpServer(service = "order.service")
public class OrderMdpServer { }
```

#### 手动指定 Topic 和 Group（可选）

```java
@MdpClient(
    service = "order.service",
    topic = "custom_order_topic"  // 覆盖自动生成的 topic
)
public interface OrderMdpClient { }

@MdpServer(
    service = "order.service",
    topic = "custom_order_topic",
    group = "custom_consumer_group",
    maxThreads = 30,
    messageModel = "BROADCASTING"  // 广播模式
)
public class OrderMdpServer { }
```

### 2. 延迟消息

#### DelayLevel 枚举值

| 枚举值 | 延迟时间 | 枚举值 | 延迟时间 |
|--------|---------|--------|---------|
| DELAY_1S | 1秒 | DELAY_6M | 6分钟 |
| DELAY_5S | 5秒 | DELAY_7M | 7分钟 |
| DELAY_10S | 10秒 | DELAY_8M | 8分钟 |
| DELAY_30S | 30秒 | DELAY_9M | 9分钟 |
| DELAY_1M | 1分钟 | DELAY_10M | 10分钟 |
| DELAY_2M | 2分钟 | DELAY_20M | 20分钟 |
| DELAY_3M | 3分钟 | DELAY_30M | 30分钟 |
| DELAY_4M | 4分钟 | DELAY_1H | 1小时 |
| DELAY_5M | 5分钟 | DELAY_2H | 2小时 |

#### 使用示例

```java
// 生产者接口
@MdpClient(service = "order.service")
public interface OrderMdpClient {
    
    @MdpMethod(isSync = false, delayLevel = DelayLevel.SECONDS_30)
    void sendOrderDelayed30s(OrderInfo order);
    
    @MdpMethod(isSync = false, delayLevel = DelayLevel.MINUTES_10)
    void sendOrderDelayed10m(OrderInfo order);
    
    @MdpMethod(isSync = false, delayLevel = DelayLevel.HOURS_1)
    void sendOrderDelayed1h(OrderInfo order);
}

// 使用
orderMdpClient.sendOrderDelayed30s(order);  // 30秒后处理
orderMdpClient.sendOrderDelayed10m(order);  // 10分钟后处理
orderMdpClient.sendOrderDelayed1h(order);   // 1小时后处理
```

### 3. 消息幂等性保证

#### 方式1：字段路径模式（推荐）

使用对象的某个字段作为业务唯一键：

```java
@Component
@MdpServer(service = "order.service")
public class OrderMdpServer {

    /**
     * 使用订单ID作为业务键
     * 框架会调用 order.getOrderId() 获取值
     */
    @Idempotent(key = "orderId", timeout = 86400)
    public void sendOrder(OrderInfo order) {
        // 相同 orderId 的消息只会处理一次
    }
    
    /**
     * 嵌套字段路径
     * 框架会调用 order.getUser().getUserId()
     */
    @Idempotent(key = "user.userId", timeout = 3600)
    public void processUserOrder(OrderInfo order) {
        // 基于用户ID去重
    }
}
```

#### 方式2：MD5 模式（零配置）

不指定 key 时，框架自动使用整个参数对象的 MD5 作为业务键：

```java
@Component
@MdpServer(service = "notification.service")
public class NotificationServer {

    /**
     * 默认使用 MD5 模式
     * 框架自动序列化 notification 并计算 MD5
     */
    @Idempotent
    public void sendNotification(NotificationInfo notification) {
        // 相同内容的消息只会处理一次
    }
}
```

#### 幂等性参数说明

```java
@Idempotent(
    enabled = true,              // 是否启用幂等性（默认 true）
    key = "orderId",             // 字段路径，不指定则使用 MD5
    timeout = 86400,             // 去重记录保留时间（秒），默认 24 小时
    lockTimeout = 10,            // 分布式锁超时时间（秒），默认 10 秒
    duplicateStrategy = SKIP     // 重复消息策略：SKIP（跳过）或 EXCEPTION（抛异常）
)
```

#### 自动选择实现方式

- **配置了 Redis**：自动使用 `RedisIdempotentService`（分布式安全，生产推荐）
- **未配置 Redis**：自动使用 `MemoryIdempotentService`（仅适合单机测试）

```yaml
# 配置 Redis 后自动启用分布式幂等性
spring:
  redis:
    host: localhost
    port: 6379
    password: your_password  # 如果有密码
```

### 4. 灵活参数转换

框架支持不同包名、不同类名之间的自动转换：

```java
// 生产者端 - 包名: com.example.producer.dto
public class OrderDTO {
    private String orderId;
    private String userId;
    private BigDecimal amount;
}

// 消费者端 - 包名: com.example.consumer.domain
public class OrderInfo {  // 不同类名
    private String orderId;
    private String userId;
    private BigDecimal amount;
}

// 自动转换，无需手动处理
@Component
@MdpServer(service = "order.service", flexibleConversion = true)
public class OrderMdpServer {
    public void sendOrder(OrderInfo order) {
        // 框架自动将 OrderDTO 转换为 OrderInfo
    }
}
```

#### 转换策略

1. **JSON 序列化/反序列化**（主要策略）
2. **字段映射反射**（回退策略）
3. **类型强制转换**（最后尝试）

设置 `flexibleConversion = false` 可以关闭灵活转换，要求类型完全匹配。

### 5. 方法名自动路由

消费者方法名必须与生产者接口方法名一致，框架自动路由：

```java
// 生产者
@MdpClient(service = "order.service")
public interface OrderMdpClient {
    void sendOrder(OrderInfo order);
    void sendOrderDelayed(OrderInfo order, DelayLevel delayLevel);
}

// 消费者 - 方法名必须一致
@Component
@MdpServer(service = "order.service")
public class OrderMdpServer {
    public void sendOrder(OrderInfo order) { }          // ✅ 正确
    public void sendOrderDelayed(OrderInfo order) { }   // ✅ 正确
    
    public void handleOrder(OrderInfo order) { }        // ❌ 错误：方法名不匹配
}
```

## 高级用法

### 消息标签（Tags）

```java
// 生产者
@MdpClient(service = "order.service")
public interface OrderMdpClient {
    
    @MdpMethod(isSync = false, tags = "VIP_ORDER")
    void sendVipOrder(OrderInfo order);
    
    @MdpMethod(isSync = false, tags = "NORMAL_ORDER")
    void sendNormalOrder(OrderInfo order);
}

// 消费者可以基于 tags 进行过滤（RocketMQ 原生功能）
```

### 广播模式

```java
@Component
@MdpServer(
    service = "notification.service",
    messageModel = "BROADCASTING"  // 广播模式，每个消费者实例都会收到消息
)
public class NotificationServer {
    public void sendNotification(NotificationInfo info) {
        // 所有消费者实例都会处理这条消息
    }
}
```

### 自定义线程数

```java
@Component
@MdpServer(
    service = "order.service",
    maxThreads = 50  // 设置最大消费线程数
)
public class OrderMdpServer {
    // 处理逻辑
}
```

## 项目结构

```
rocketmq-mdp-enhanced/
├── pom.xml
├── README.md
├── README_IDEMPOTENT.md          # 幂等性详细文档
├── QUICK_START.md                # 快速开始指南
├── 幂等性测试说明.md
├── src/main/java/com/rocketmq/mdp/enhanced/
│   ├── annotation/               # 注解定义
│   │   ├── EnableMdp.java        # 启用框架
│   │   ├── MdpClient.java        # 生产者注解
│   │   ├── MdpServer.java        # 消费者注解
│   │   ├── MdpMethod.java        # 方法注解
│   │   └── Idempotent.java       # 幂等性注解
│   ├── config/                   # 自动配置
│   │   ├── MdpAutoConfiguration.java
│   │   └── IdempotentConfig.java
│   ├── register/                 # 注册器
│   │   ├── MdpClientClassRegister.java
│   │   └── MdpServerClassRegister.java
│   ├── generator/                # 动态代理生成
│   │   └── MdpInterfaceGenerator.java
│   ├── consumer/                 # 消费者监听器
│   │   └── MdpMessageListener.java
│   ├── converter/                # 参数转换器
│   │   └── ParameterConverter.java
│   ├── idempotent/               # 幂等性实现
│   │   ├── IdempotentService.java
│   │   ├── IdempotentProcessor.java
│   │   ├── RedisIdempotentService.java
│   │   └── MemoryIdempotentService.java
│   ├── domain/                   # 领域模型
│   │   └── MdpMessage.java
│   └── enums/                    # 枚举定义
│       └── DelayLevel.java
└── mdp-example/                  # 使用示例
    └── src/main/java/com/example/mdp/
        ├── MdpExampleApplication.java
        ├── client/
        │   ├── OrderMdpClient.java
        │   └── VipOrderMdpClient.java
        ├── server/
        │   ├── OrderMdpServer.java
        │   └── VipOrderMdpServer.java
        ├── domain/
        │   └── OrderInfo.java
        └── config/
            └── RedisConfig.java
```

## 依赖要求

- Java 8+
- Spring Boot 2.3.12+
- RocketMQ Client 4.9.4+
- RocketMQ Spring Boot Starter 2.2.1+
- Jackson 2.11.4+
- Redis（可选，用于分布式幂等性）

## 常见问题

### Q: 生产者/消费者没有注册？

**A**: 检查以下几点：
1. 是否添加了 `@EnableMdp` 注解
2. `rocketmq.name-server` 是否配置正确
3. 生产者接口/消费者类是否在扫描包路径下

### Q: 消息没有收到？

**A**: 
1. 确保 Topic 名称匹配（生产者和消费者的 service 值相同）
2. 检查消费者类是否标注了 `@Component`
3. 确认消费者方法名与生产者接口方法名一致
4. 确认 RocketMQ 服务器正常运行

### Q: 参数转换失败？

**A**: 
1. 确保字段名称匹配
2. 检查字段类型是否兼容
3. 确认开启了 `flexibleConversion = true`（默认开启）

### Q: 幂等性不生效？

**A**:
1. 检查是否添加了 `@Idempotent` 注解
2. 如果使用字段路径模式，确认字段名和 getter 方法存在
3. 如果使用 Redis 模式，确认 Redis 连接正常
4. 查看日志，确认 IdempotentService 加载正确

### Q: 延迟消息不生效？

**A**:
1. 确认 `@MdpMethod` 注解设置了正确的 `delayLevel` 参数
2. 确认 RocketMQ Broker 启用了延迟消息功能
3. 检查延迟级别是否在 1-18 范围内

## 构建项目

```bash
cd E:/IdeaProjects/rocketmq-mdp-enhanced
mvn clean package -DskipTests
```

JAR 包位置: `target/rocketmq-mdp-enhanced-1.0.0.jar`

## 许可证

Apache License 2.0
