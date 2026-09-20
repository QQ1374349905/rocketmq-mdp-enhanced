# 增强版 MDP (Message-Driven Programming) 库

## 概述

`gaoji-common-mdp-enhanced` 是基于原始 `gaoji-common-mdp-3.2.0.jar` 的增强版本，完全兼容原有架构，新增两大核心功能：

1. **延迟异步消息支持** - 支持18个延迟级别（1秒到2小时）
2. **灵活参数转换** - 自动转换不同包名、不同类之间的参数，无需强制要求相同包名

## 核心特性

### 1. 非侵入式配置 ✨
- **自动生成Topic**: 基于service名称自动生成topic
- **自动生成Consumer Group**: 格式为 `{service}_consumer_group`
- **零配置**: 仅需RocketMQ的name-server配置
- **无需手动配置**: Topic和Group自动创建

### 2. 延迟消息支持 ⏰
- 支持18个延迟级别：1s, 5s, 10s, 30s, 1m, 2m, 3m, 4m, 5m, 6m, 7m, 8m, 9m, 10m, 20m, 30m, 1h, 2h
- 同步和异步发送
- 简单易用的API

### 3. 灵活参数转换 🔄
- 自动转换不同包名的类
- 支持字段子集转换
- 多种转换策略（JSON、字段映射、类型强制转换）

## 与原始MDP的对比

| 特性 | 原始MDP | 增强版MDP |
|------|---------|-----------|
| 生产者定义 | @MdpC | @MdpCEnhanced |
| 消费者定义 | @MdpS | @MdpSEnhanced |
| 方法定义 | @MdpMethod | @MdpMethodEnhanced |
| Topic配置 | 手动配置 | 自动生成（service名称） |
| Group配置 | 手动配置 | 自动生成（service + "_consumer_group"） |
| 延迟消息 | 不支持 | 支持（18个级别） |
| 参数转换 | 需要相同包名 | 灵活转换，不同包名也可以 |
| 实现方式 | 动态代理 | 动态代理（兼容原有架构） |

## 安装

### Maven依赖

```xml
<dependency>
    <groupId>com.gaoji</groupId>
    <artifactId>gaoji-common-mdp-enhanced</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 配置

仅需配置RocketMQ服务器地址：

```yaml
# application.yml
rocketmq:
  name-server: 127.0.0.1:9876
```

## 使用示例

### 1. 定义生产者（Producer）

```java
import com.gaoji.common.mdp.enhanced.annotation.MdpClient;
import com.gaoji.common.mdp.enhanced.annotation.MdpMethod;
import com.gaoji.common.mdp.enhanced.annotation.MdpMethodEnhanced;

/**
 * 生产者接口 - 使用 @MdpCEnhanced 标注
 * Topic自动生成为: "nldb.trade.order.ret"
 */
@MdpClient(service = "nldb.trade.order.ret")
public interface OrderRetMdpEnhanced {

    /**
     * 发送普通异步消息
     */
    @MdpMethod(isSync = false)
    void onMessage(TradeMqResponse response);

    /**
     * 发送延迟异步消息
     * @param response 消息体
     * @param delayLevel 延迟级别（1-18）
     */
    @MdpMethod(isSync = false, supportDelay = true)
    void onMessageDelayed(TradeMqResponse response, int delayLevel);

    /**
     * 发送同步消息（等待返回结果）
     */
    @MdpMethod(isSync = true)
    SendResult onMessageSync(TradeMqResponse response);
}
```

### 2. 使用生产者发送消息

```java
@Service
public class OrderService {

    // 直接注入接口，框架自动生成实现类
    @Autowired
    private OrderRetMdpEnhanced orderRetMdp;

    // 发送普通消息
    public void sendResponse(TradeMqResponse response) {
        orderRetMdp.onMessage(response);
    }

    // 发送延迟消息（30秒后处理）
    public void sendDelayedResponse(TradeMqResponse response) {
        orderRetMdp.onMessageDelayed(response, 4); // 4 = 30秒
    }

    // 发送延迟消息（1分钟后处理）
    public void sendDelayedReminder(TradeMqResponse response) {
        orderRetMdp.onMessageDelayed(response, 5); // 5 = 1分钟
    }

    // 发送延迟消息（10分钟后处理）
    public void sendDelayedCancel(TradeMqResponse response) {
        orderRetMdp.onMessageDelayed(response, 14); // 14 = 10分钟
    }
}
```

### 3. 定义消费者（Consumer）

```java
import com.gaoji.common.mdp.enhanced.annotation.MdpServer;
import org.springframework.stereotype.Component;

/**
 * 消费者类 - 使用 @MdpSEnhanced 标注
 * Topic自动生成为: "nldb.trade.order"
 * Consumer Group自动生成为: "nldb.trade.order_consumer_group"
 */
@Component
@MdpServer(
        service = "nldb.trade.order",
        maxThreads = 20,
        flexibleConversion = true  // 开启灵活参数转换
)
public class OrderConsumerEnhanced {

    /**
     * 消息处理方法，方法名必须是 onMessage
     * 参数类型可以与生产者不同，框架会自动转换
     */
    public void onMessage(OrderNotify orderNotify) {
        // 处理订单消息
        System.out.println("收到订单: " + orderNotify.getOrderNumber());

        // 业务逻辑...
    }
}
```

### 4. 灵活参数转换示例

增强版MDP支持不同包名、不同类之间的自动转换：

```java
// 生产者端 - 包名: com.gaoji.trade.api.dto
public class OrderDTO {
    private String orderNumber;
    private String orderMoney;
    private String orderStatus;
    // getters and setters
}

// 消费者端 - 包名: com.gaoji.settlement.domain
public class OrderNotify {  // 不同包名，不同类名
    private String orderNumber;
    private String orderMoney;
    private String orderStatus;
    // getters and setters
}

// 消费者自动转换，无需手动处理
@MdpSEnhanced(service = "order.notify", flexibleConversion = true)
public class OrderConsumer {
    public void onMessage(OrderNotify order) {
        // 自动从 OrderDTO 转换为 OrderNotify
    }
}
```

## 延迟级别参考表

| 级别 | 延迟时间 | 级别 | 延迟时间 |
|------|---------|------|---------|
| 1    | 1秒     | 10   | 6分钟   |
| 2    | 5秒     | 11   | 7分钟   |
| 3    | 10秒    | 12   | 8分钟   |
| 4    | 30秒    | 13   | 9分钟   |
| 5    | 1分钟   | 14   | 10分钟  |
| 6    | 2分钟   | 15   | 20分钟  |
| 7    | 3分钟   | 16   | 30分钟  |
| 8    | 4分钟   | 17   | 1小时   |
| 9    | 5分钟   | 18   | 2小时   |

## 从原始MDP迁移

### 原始MDP写法

```java
// 生产者
@MdpC(service = "nldb.trade.order.ret")
public interface OrderRetMdp {
    @MdpMethod(isSync = false)
    void onMessage(TradeMqResponse response);
}

// 消费者
@MdpS(service = "nldb.trade.order")
public class OrderConsumer {
    public void notifyOrder(OrderNotify orderNotify) {
        // 处理消息
    }
}
```

### 增强版MDP写法

```java
// 生产者 - 支持延迟消息
@MdpCEnhanced(service = "nldb.trade.order.ret")
public interface OrderRetMdpEnhanced {
    @MdpMethodEnhanced(isSync = false)
    void onMessage(TradeMqResponse response);
    
    // 新增：延迟消息方法
    @MdpMethodEnhanced(isSync = false, supportDelay = true)
    void onMessageDelayed(TradeMqResponse response, int delayLevel);
}

// 消费者 - 支持灵活参数转换
@Component
@MdpSEnhanced(service = "nldb.trade.order", flexibleConversion = true)
public class OrderConsumerEnhanced {
    public void onMessage(OrderNotify orderNotify) {
        // 处理消息 - 自动转换不同包名的类
    }
}
```

## 高级配置

### 自定义Topic和Group

```java
@MdpSEnhanced(
    service = "order.service",
    topic = "custom_topic",           // 覆盖自动生成的topic
    group = "custom_consumer_group",  // 覆盖自动生成的group
    maxThreads = 30,
    messageModel = "BROADCASTING"     // CLUSTERING（默认）或 BROADCASTING
)
public class CustomConsumer {
    public void onMessage(OrderInfo order) {
        // 处理消息
    }
}
```

### 关闭灵活参数转换

```java
@MdpSEnhanced(
    service = "order.service",
    flexibleConversion = false  // 关闭灵活转换，要求类型完全匹配
)
public class StrictConsumer {
    public void onMessage(OrderInfo order) {
        // 处理消息
    }
}
```

## 项目结构

```
gaoji-common-mdp-enhanced/
├── pom.xml
├── README.md
├── QUICK_START.md
├── BUILD_GUIDE.md
└── src/main/java/com/gaoji/common/mdp/enhanced/
    ├── annotation/           # 注解定义
    │   ├── MdpCEnhanced.java       # 生产者注解
    │   ├── MdpSEnhanced.java       # 消费者注解
    │   └── MdpMethodEnhanced.java  # 方法注解
    ├── domain/              # 领域模型
    │   └── EnhancedMdpMessage.java # 消息包装类
    ├── converter/           # 参数转换器
    │   └── FlexibleParameterConverter.java
    ├── generator/           # 代理生成器
    │   └── EnhancedMdpInterfaceGenerator.java
    ├── consumer/            # 消费者组件
    │   └── EnhancedMdpMessageListener.java
    ├── register/            # 注册器
    │   ├── EnhancedMdpCClassRegister.java  # 生产者注册器
    │   └── EnhancedMdpSClassRegister.java  # 消费者注册器
    ├── config/              # 自动配置
    │   └── EnhancedMdpAutoConfiguration.java
    └── example/             # 使用示例
        ├── OrderRetMdpEnhanced.java
        ├── OrderConsumerEnhanced.java
        ├── OrderBusinessService.java
        ├── OrderNotify.java
        └── TradeMqResponse.java
```

## 依赖要求

- Spring Boot 2.3.12+
- RocketMQ Client 4.9.4+
- Jackson 2.11.4+
- Java 8+

## 构建JAR包

```bash
cd E:/IdeaProjects/gaoji-nldb-settlement/gaoji-common-mdp-enhanced
mvn clean package -DskipTests
```

JAR包位置: `target/gaoji-common-mdp-enhanced-1.0.0.jar`

## 常见问题

### Q: 生产者/消费者没有注册？
A: 检查 `rocketmq.name-server` 是否配置正确

### Q: 消息没有收到？
A: 
1. 确保Topic名称匹配（都使用相同的service值）
2. 检查消费者类是否标注了 `@Component`
3. 确认RocketMQ服务器正常运行

### Q: 参数转换失败？
A: 
1. 确保字段名称匹配
2. 检查字段类型是否兼容
3. 确认开启了 `flexibleConversion = true`

## 许可证

Copyright © 2024 Gaoji Technology
