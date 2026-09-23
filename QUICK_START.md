# Quick Start Guide - MDP Framework

## 5分钟快速开始

### Step 1: 添加依赖 (1 min)

添加到项目的 `pom.xml`:

```xml
<dependency>
    <groupId>com.rocketmq</groupId>
    <artifactId>rocketmq-mdp-enhanced</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Step 2: 配置 RocketMQ (1 min)

添加到 `application.yml`:

```yaml
rocketmq:
  name-server: 127.0.0.1:9876
```

### Step 3: 启用 MDP 框架

```java
import com.rocketmq.mdp.enhanced.annotation.EnableMdp;

@SpringBootApplication
@EnableMdp
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### Step 4: 创建生产者接口 (1 min)

```java
import com.rocketmq.mdp.enhanced.annotation.MdpClient;
import com.rocketmq.mdp.enhanced.annotation.MdpMethod;
import com.rocketmq.mdp.enhanced.enums.DelayLevel;

@MdpClient(service = "my.service")
public interface MyServiceClient {

    @MdpMethod(isSync = false)
    void sendMessage(MyData data);

    @MdpMethod(isSync = false, delayLevel = DelayLevel.MINUTES_1)
    void sendDelayedMessage(MyData data);
}
```

### Step 5: 发送消息 (1 min)

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MyService {
    
    @Autowired
    private MyServiceClient client;
    
    public void sendMessage(MyData data) {
        client.sendMessage(data);  // 立即发送
    }
    
    public void sendDelayedMessage(MyData data) {
        client.sendDelayedMessage(data);  // 1分钟后发送
    }
}
```

### Step 6: 创建消费者 (1 min)

```java
import com.rocketmq.mdp.enhanced.annotation.MdpServer;
import org.springframework.stereotype.Component;

@Component
@MdpServer(service = "my.service")
public class MyConsumer {

    // 方法名必须与生产者接口方法名一致
    public void sendMessage(MyData data) {
        System.out.println("收到消息: " + data);
        // 你的业务逻辑
    }
    
    public void sendDelayedMessage(MyData data) {
        System.out.println("收到延迟消息: " + data);
        // 你的业务逻辑
    }
}
```

## 完成！🎉

你的 MDP 框架现在可以：
- ✅ 自动生成 topics 和 groups
- ✅ 发送延迟消息
- ✅ 灵活转换参数
- ✅ 处理异步消息
- ✅ 支持消息幂等性

## 常见用例

### 用例 1: 发送延迟订单

```java
// 10秒后处理订单
OrderInfo order = new OrderInfo("ORD001", "USER123", "商品A", new BigDecimal("100.00"));
orderClient.sendDelayedMessage(order, DelayLevel.DELAY_10S);
```

### 用例 2: 消息幂等性

```java
import com.rocketmq.mdp.enhanced.annotation.Idempotent;

@Component
@MdpServer(service = "order.service")
public class OrderConsumer {
    
    // 防止重复消息处理
    @Idempotent(key = "orderId", timeout = 86400)
    public void sendOrder(OrderInfo order) {
        // 每个 orderId 只会处理一次
        processOrder(order);
    }
}
```

### 用例 3: 灵活参数转换

```java
// 生产者发送 OrderDTO (包名: com.example.dto)
public class OrderDTO {
    private String orderId;
    private BigDecimal amount;
}

// 消费者接收为 OrderInfo (包名: com.example.model)
public void sendOrder(OrderInfo order) {  // 自动转换！
    // 不同包名，相同字段 - 自动转换
}
```

## 延迟级别快速参考

| 枚举值 | 时间 | 用途 |
|--------|------|------|
| DELAY_1S | 1秒 | 快速重试 |
| DELAY_10S | 10秒 | 短延迟 |
| DELAY_1M | 1分钟 | 标准延迟 |
| DELAY_5M | 5分钟 | 中等延迟 |
| DELAY_10M | 10分钟 | 长延迟 |
| DELAY_1H | 1小时 | 很长延迟 |

## 下一步

- 阅读 [README.md](README.md) 获取详细文档
- 查看 [README_IDEMPOTENT.md](README_IDEMPOTENT.md) 了解幂等性指南
- 查看 `mdp-example/` 模块中的示例代码

## 故障排除

**Q: 生产者/消费者不工作？**
- 检查 `rocketmq.name-server` 是否配置
- 检查是否添加了 `@EnableMdp` 注解
- 验证 RocketMQ 服务器是否运行

**Q: 消息没有收到？**
- 确保 topic 名称匹配（都使用相同的 service 名称）
- 检查消费者是否标注了 `@Component`
- 验证方法名是否与生产者接口方法名一致

**Q: 参数转换失败？**
- 确保字段名匹配
- 检查两个类都有 getters/setters
- 验证字段类型兼容
