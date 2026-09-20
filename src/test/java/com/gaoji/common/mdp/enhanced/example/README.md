# MDP框架使用示例

这是一个完整的MDP框架使用示例，演示了如何使用增强版MDP进行消息生产和消费。

## 项目结构

```
example/
├── MdpExampleApplication.java    # Spring Boot启动类
├── MdpExampleTest.java            # 测试用例
├── client/
│   └── OrderMdpClient.java        # MDP生产者接口
├── server/
│   └── OrderMdpServer.java        # MDP消费者实现
└── domain/
    └── OrderInfo.java             # 领域对象
```

## 核心概念

### 1. MDP生产者 (@MdpClient)

使用 `@MdpClient` 标注接口，框架会自动生成实现类：

```java
@MdpClient(
    service = "order.service",       // 服务名，自动生成topic
    sendTimeout = 5000,              // 发送超时时间
    flexibleConversion = true        // 支持灵活参数转换
)
public interface OrderMdpClient {
    
    @MdpMethod(isSync = true)
    void sendOrder(OrderInfo order);  // 同步发送
    
    @MdpMethod(isSync = false)
    void sendOrderAsync(OrderInfo order);  // 异步发送
    
    @MdpMethod(isSync = false, supportDelay = true)
    void sendOrderDelayed(OrderInfo order, int delayLevel);  // 延迟发送
}
```

### 2. MDP消费者 (@MdpServer)

使用 `@MdpServer` 标注类，实现 `onMessage` 方法：

```java
@Component
@MdpServer(
    service = "order.service",       // 服务名，必须与生产者一致
    maxThreads = 20,                 // 最大消费线程数
    messageModel = "CLUSTERING",     // 集群模式
    flexibleConversion = true        // 支持灵活参数转换
)
public class OrderMdpServer {
    
    public void onMessage(OrderInfo order) {
        // 处理消息
    }
}
```

## 功能特性

### 1. 同步/异步发送

```java
// 同步发送：等待发送结果
orderMdpClient.sendOrder(order);

// 异步发送：不等待发送结果
orderMdpClient.sendOrderAsync(order);
```

### 2. 延迟消息

RocketMQ支持18个延迟级别：

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

```java
// 延迟10秒后被消费
orderMdpClient.sendOrderDelayed(order, 3);
```

### 3. 消息标签（Tags）

用于消息过滤：

```java
@MdpMethod(isSync = false, tags = "VIP_ORDER")
void sendVipOrder(OrderInfo order);
```

### 4. 灵活参数转换

支持生产者和消费者使用不同包名的同名类：

```java
// 生产者：com.producer.domain.OrderInfo
// 消费者：com.consumer.domain.OrderInfo
// 框架会自动转换
```

## 运行测试

### 前置条件

1. 安装并启动RocketMQ：

```bash
# 启动NameServer
nohup sh bin/mqnamesrv &

# 启动Broker
nohup sh bin/mqbroker -n localhost:9876 &
```

2. 配置RocketMQ地址（application.yml）：

```yaml
rocketmq:
  name-server: localhost:9876
```

### 运行测试用例

```bash
# 运行所有测试
mvn test -Dtest=MdpExampleTest

# 运行单个测试
mvn test -Dtest=MdpExampleTest#testSendOrderSync
mvn test -Dtest=MdpExampleTest#testSendOrderAsync
mvn test -Dtest=MdpExampleTest#testSendOrderDelayed
```

## 测试用例说明

| 测试方法 | 说明 |
|---------|------|
| testSendOrderSync | 测试同步发送消息 |
| testSendOrderAsync | 测试异步发送消息 |
| testSendOrderDelayed | 测试延迟消息（延迟10秒） |
| testSendVipOrder | 测试带标签的消息 |
| testBatchSendOrders | 测试批量发送10条消息 |
| testFlexibleConversion | 测试灵活参数转换功能 |

## 注意事项

1. **服务名匹配**：生产者和消费者的 `service` 参数必须一致
2. **延迟消息**：使用 `supportDelay=true` 时，方法必须有 `int delayLevel` 参数
3. **消费方法**：消费者类必须实现 `onMessage` 方法
4. **Spring组件**：消费者类必须添加 `@Component` 注解
5. **RocketMQ配置**：确保 `name-server` 地址正确

## 故障排查

### 问题1：消息发送失败

检查：
- RocketMQ是否正常运行
- name-server地址配置是否正确
- 网络连接是否正常

### 问题2：消息未被消费

检查：
- 消费者是否正确注册（查看日志）
- topic和group是否正确
- 消费者线程是否正常

### 问题3：延迟消息不生效

检查：
- delayLevel参数是否在1-18范围内
- Broker是否支持延迟消息

## 扩展示例

### 多消费者场景

```java
// 消费者1：集群模式
@MdpServer(service = "order.service", messageModel = "CLUSTERING")
public class OrderMdpServer1 { ... }

// 消费者2：广播模式（每个消费者都会收到消息）
@MdpServer(service = "order.service", messageModel = "BROADCASTING")
public class OrderMdpServer2 { ... }
```

### 自定义Topic和Group

```java
@MdpClient(
    service = "order.service",
    topic = "custom_topic",        // 自定义topic
    sendTimeout = 5000
)
public interface CustomMdpClient { ... }

@MdpServer(
    service = "order.service",
    topic = "custom_topic",        // 自定义topic
    group = "custom_group"         // 自定义group
)
public class CustomMdpServer { ... }
```

## 性能优化建议

1. **异步发送**：非关键消息使用异步发送提高吞吐量
2. **批量发送**：大量消息时使用批量发送
3. **线程数调整**：根据业务调整 `maxThreads` 参数
4. **消息压缩**：大消息启用压缩（compress-message-body-threshold）

## 更多信息

- [RocketMQ官方文档](https://rocketmq.apache.org/)
- [项目GitHub地址](https://github.com/your-repo/gaoji-common-mdp-enhanced)
