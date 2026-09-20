# 增强版MDP - 方法命名灵活性说明

## ✅ 支持任意方法名

增强版MDP的消费者**不再强制要求方法名为`onMessage`**，支持任意方法名！

### 方法查找优先级

1. **只有一个public方法** → 自动使用该方法
2. **有多个public方法** → 优先查找`onMessage`，如果没有则使用第一个
3. **继承的方法** → 会查找父类的public方法

## 使用示例

### 示例1：任意方法名（推荐）

```java
@Component
@MdpSEnhanced(service = "nldb.trade.order")
public class OrderConsumer {
    
    // 方法名可以是任意的
    public void handleOrder(OrderNotify order) {
        System.out.println("处理订单: " + order.getOrderNumber());
    }
}
```

### 示例2：使用aaa方法名

```java
@Component
@MdpSEnhanced(service = "nldb.trade.order")
public class OrderConsumer {
    
    public void aaa(OrderNotify order) {
        System.out.println("收到消息: " + order);
    }
}
```

### 示例3：多个方法时使用onMessage

```java
@Component
@MdpSEnhanced(service = "nldb.trade.order")
public class OrderConsumer {
    
    // 如果有多个public方法，优先使用onMessage
    public void onMessage(OrderNotify order) {
        System.out.println("这个方法会被调用");
    }
    
    // 这个方法会被忽略
    public void anotherMethod(OrderNotify order) {
        System.out.println("这个不会被调用");
    }
}
```

### 示例4：生产者也支持任意方法名

```java
@MdpCEnhanced(service = "nldb.trade.order.ret")
public interface OrderRetMdp {
    
    // 方法名可以是任意的
    @MdpMethodEnhanced(isSync = false)
    void sendResponse(TradeMqResponse response);
    
    @MdpMethodEnhanced(isSync = false, supportDelay = true)
    void sendDelayedResponse(TradeMqResponse response, int delayLevel);
}

// 使用
@Service
public class OrderService {
    @Autowired
    private OrderRetMdp orderRetMdp;
    
    public void process() {
        orderRetMdp.sendResponse(new TradeMqResponse());
        orderRetMdp.sendDelayedResponse(new TradeMqResponse(), 5);
    }
}
```

## 完整示例：任意方法名

### 生产者接口

```java
@MdpCEnhanced(service = "order.payment")
public interface PaymentMdp {
    
    @MdpMethodEnhanced(isSync = false)
    void notifyPayment(PaymentInfo payment);
    
    @MdpMethodEnhanced(isSync = false, supportDelay = true)
    void delayedCheck(PaymentInfo payment, int delayLevel);
}
```

### 消费者类

```java
@Component
@MdpSEnhanced(service = "order.payment", flexibleConversion = true)
public class PaymentConsumer {
    
    // 方法名自定义
    public void processPayment(PaymentInfo payment) {
        System.out.println("处理支付: " + payment.getPaymentId());
    }
}
```

### 业务服务

```java
@Service
public class PaymentService {
    
    @Autowired
    private PaymentMdp paymentMdp;
    
    public void createPayment(PaymentInfo payment) {
        // 发送普通消息
        paymentMdp.notifyPayment(payment);
        
        // 30秒后检查支付状态
        paymentMdp.delayedCheck(payment, 4);
    }
}
```

## 最佳实践

1. **单一方法消费者** - 方法名可以任意取，建议使用语义化命名
   ```java
   public void handleOrder(OrderNotify order) { }
   public void processPayment(PaymentInfo payment) { }
   ```

2. **多方法消费者** - 使用`onMessage`作为消息处理方法
   ```java
   public void onMessage(OrderNotify order) { }  // 会被调用
   private void helperMethod() { }               // 辅助方法
   ```

3. **生产者接口** - 使用描述性的方法名
   ```java
   void sendOrder(OrderInfo order);
   void sendDelayedOrder(OrderInfo order, int delayLevel);
   ```

## 注意事项

- ✅ 方法必须是**public**
- ✅ 方法必须有**至少一个参数**（消息体）
- ✅ 方法不能是**static**
- ✅ 如果有多个public方法且都有参数，优先使用`onMessage`，否则使用第一个

## 与原始MDP对比

| 特性 | 原始MDP | 增强版MDP |
|------|---------|-----------|
| 消费者方法名 | 任意 | 任意（优先onMessage） |
| 生产者方法名 | 任意 | 任意 |
| 方法查找 | 自动 | 智能查找 |

完全兼容原始MDP的灵活性！🎉
