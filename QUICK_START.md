# Quick Start Guide - Enhanced MDP

## 5-Minute Quick Start

### Step 1: Add Dependency (1 min)

Add to your project's `pom.xml`:

```xml
<dependency>
    <groupId>com.gaoji</groupId>
    <artifactId>gaoji-common-mdp-enhanced</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Step 2: Configure RocketMQ (1 min)

Add to `application.yml`:

```yaml
rocketmq:
  name-server: 127.0.0.1:9876
```

### Step 3: Create Producer Interface (1 min)

```java
import com.gaoji.common.mdp.enhanced.annotation.MdpClient;
import com.gaoji.common.mdp.enhanced.annotation.MdpMethod;

@MdpClient(service = "my.service")
public interface MyServiceClient {
    
    @MdpMethod(isSync = false)
    void sendMessage(MyData data);
    
    @MdpMethod(isSync = false, supportDelay = true)
    void sendDelayedMessage(MyData data, int delayLevel);
}
```

### Step 4: Send Messages (1 min)

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MyService {
    
    @Autowired
    private MyServiceClient client;
    
    public void sendMessage(MyData data) {
        client.sendMessage(data);  // Send immediately
    }
    
    public void sendDelayedMessage(MyData data) {
        client.sendDelayedMessage(data, 5);  // Send with 1-minute delay
    }
}
```

### Step 5: Create Consumer (1 min)

```java
import com.gaoji.common.mdp.enhanced.annotation.MdpServer;
import org.springframework.stereotype.Component;

@Component
@MdpServer(service = "my.service")
public class MyConsumer {
    
    public void onMessage(MyData data) {
        System.out.println("Received: " + data);
        // Your business logic here
    }
}
```

## Done! 🎉

Your enhanced MDP is now ready to:
- ✅ Auto-generate topics and groups
- ✅ Send delayed messages
- ✅ Convert parameters flexibly
- ✅ Handle async messages
- ✅ Support message idempotency

## Common Use Cases

### Use Case 1: Send Order with Delay

```java
// Send order for processing after 10 seconds
OrderDTO order = new OrderDTO("ORD001", "ORDER123", new BigDecimal("100.00"));
orderClient.sendDelayedMessage(order, 3);  // delay level 3 = 10 seconds
```

### Use Case 2: Message Idempotency

```java
@Component
@MdpServer(service = "order.service")
public class OrderConsumer {
    
    // Prevent duplicate message processing
    @Idempotent(key = "orderId", timeout = 86400)
    public void onMessage(OrderInfo order) {
        // This will only execute once per orderId
        processOrder(order);
    }
}
```

### Use Case 3: Flexible Parameter Conversion

```java
// Producer sends OrderDTO (package: com.example.dto)
public class OrderDTO {
    private String orderId;
    private BigDecimal amount;
}

// Consumer receives as OrderInfo (package: com.example.model)
public void onMessage(OrderInfo order) {  // Auto-converted!
    // Different package, same fields - works automatically
}
```

## Delay Levels Quick Reference

| Level | Time | Usage |
|-------|------|-------|
| 1 | 1s | Quick retry |
| 3 | 10s | Short delay |
| 5 | 1m | Standard delay |
| 9 | 5m | Medium delay |
| 14 | 10m | Long delay |
| 17 | 1h | Very long delay |

## Next Steps

- Read [README.md](README.md) for detailed documentation
- Check [README_IDEMPOTENT.md](README_IDEMPOTENT.md) for idempotency guide
- Review example code in `mdp-example/` module

## Troubleshooting

**Q: Producer/Consumer not working?**
- Check `rocketmq.name-server` is configured
- Verify RocketMQ server is running

**Q: Message not received?**
- Ensure topic names match (both use same service name)
- Check consumer is annotated with `@Component`
- Verify method is named `onMessage`

**Q: Parameter conversion failed?**
- Ensure field names match
- Check both classes have getters/setters
- Verify field types are compatible
