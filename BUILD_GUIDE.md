# Build and Installation Guide

## Building the JAR

### Option 1: Using Maven Command Line

```bash
cd E:/IdeaProjects/gaoji-nldb-settlement/gaoji-common-mdp-enhanced
mvn clean package -DskipTests
```

The JAR will be created at: `target/gaoji-common-mdp-enhanced-1.0.0.jar`

### Option 2: Using IntelliJ IDEA

1. Open IntelliJ IDEA
2. Right-click on `gaoji-common-mdp-enhanced` module
3. Select `Maven` → `Reimport`
4. Right-click on `gaoji-common-mdp-enhanced` module again
5. Select `Maven` → `Lifecycle` → `package`
6. The JAR will be generated in the `target` folder

### Option 3: Using Eclipse/STS

1. Right-click on the project
2. Select `Run As` → `Maven build...`
3. In Goals, enter: `clean package -DskipTests`
4. Click `Run`

## Installing to Local Maven Repository

After building, install to your local Maven repository:

```bash
mvn clean install -DskipTests
```

Or manually install:

```bash
mvn install:install-file \
  -Dfile=target/gaoji-common-mdp-enhanced-1.0.0.jar \
  -DgroupId=com.gaoji \
  -DartifactId=gaoji-common-mdp-enhanced \
  -Dversion=1.0.0 \
  -Dpackaging=jar
```

## Using the Enhanced MDP in Your Project

### 1. Add Dependency to pom.xml

```xml
<dependency>
    <groupId>com.gaoji</groupId>
    <artifactId>gaoji-common-mdp-enhanced</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Configure RocketMQ

Add to `application.yml`:

```yaml
rocketmq:
  name-server: 127.0.0.1:9876  # Your RocketMQ server address
```

Or `application.properties`:

```properties
rocketmq.name-server=127.0.0.1:9876
```

### 3. Enable Enhanced MDP (Optional)

Add to your Spring Boot main application class:

```java
@SpringBootApplication
@EnableEnhancedMdp  // Optional - auto-configuration works without this
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

## Project Structure

```
gaoji-common-mdp-enhanced/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/gaoji/common/mdp/enhanced/
│       │       ├── annotation/           # Annotations
│       │       │   ├── EnableEnhancedMdp.java
│       │       │   ├── MdpConsumer.java
│       │       │   ├── MdpProducer.java
│       │       │   └── MdpHandler.java
│       │       ├── config/              # Auto-configuration
│       │       │   └── EnhancedMdpAutoConfiguration.java
│       │       ├── consumer/            # Consumer components
│       │       │   └── EnhancedMdpMessageListener.java
│       │       ├── converter/           # Parameter conversion
│       │       │   └── FlexibleParameterConverter.java
│       │       ├── domain/              # Domain models
│       │       │   └── EnhancedMdpMessage.java
│       │       ├── example/             # Usage examples
│       │       │   ├── OrderDTO.java
│       │       │   ├── OrderInfo.java
│       │       │   ├── OrderPaymentProducer.java
│       │       │   ├── OrderPaymentConsumer.java
│       │       │   └── OrderService.java
│       │       ├── producer/            # Producer components
│       │       │   └── EnhancedMdpProducer.java
│       │       └── registry/            # Registration components
│       │           └── EnhancedMdpRegistry.java
│       └── resources/
│           └── META-INF/
│               └── spring.factories     # Spring Boot auto-configuration
├── pom.xml
└── README.md
```

## Key Features Summary

### 1. Auto-Generated Topic and Group
- **Topic**: Automatically uses service name as topic
- **Consumer Group**: Automatically generates as `{service}_consumer_group`
- **Example**: Service `order.payment` → Topic: `order.payment`, Group: `order.payment_consumer_group`

### 2. Delayed Message Support
- 18 delay levels from 1 second to 2 hours
- Both sync and async sending
- Simple API: `producer.sendDelayed(message, delayLevel)`

### 3. Flexible Parameter Conversion
- Automatic conversion between different class types
- No requirement for same package names
- Multiple conversion strategies:
  - JSON serialization/deserialization
  - Field-by-field mapping
  - Type coercion for primitives

## Comparison with Original MDP

| Feature | Original MDP | Enhanced MDP |
|---------|-------------|--------------|
| Topic Configuration | Manual | Auto-generated from service name |
| Group Configuration | Manual | Auto-generated from service name |
| Delayed Messages | Not supported | Supported (18 levels) |
| Parameter Conversion | Same package required | Flexible cross-package |
| Async Messages | Limited | Full support with callbacks |
| Configuration | More verbose | Minimal, non-invasive |

## Examples

### Producer Example

```java
@Component
@MdpProducer(service = "order.payment")
public class OrderPaymentProducer {
}

@Service
public class OrderService {
    @Autowired
    @Qualifier("enhancedMdpProducer_order.payment")
    private EnhancedMdpProducer producer;
    
    public void sendOrder(OrderDTO order) {
        // Normal send
        producer.send(order);
        
        // Delayed send (1 minute)
        producer.sendDelayed(order, 5);
        
        // Async send
        producer.sendAsync(order, callback);
    }
}
```

### Consumer Example

```java
@Component
@MdpConsumer(service = "order.payment")
public class OrderPaymentConsumer {
    
    @MdpHandler
    public void handleOrder(OrderInfo order) {
        // Process order - automatic conversion from OrderDTO to OrderInfo
        System.out.println("Received: " + order.getOrderId());
    }
}
```

## Troubleshooting

### Maven Build Issues

If Maven build fails, check:
1. Java version is 1.8 or higher: `java -version`
2. Maven is installed: `mvn -version`
3. Internet connection for dependency downloads
4. Maven settings.xml is properly configured

### RocketMQ Connection Issues

If messages are not sent/received:
1. Verify RocketMQ server is running
2. Check `rocketmq.name-server` configuration
3. Verify network connectivity to RocketMQ server
4. Check firewall settings

### Parameter Conversion Issues

If parameter conversion fails:
1. Ensure field names match between source and target
2. Check field types are compatible
3. Add proper getters/setters to both classes
4. Review logs for detailed error messages

## Support

For issues or questions:
- Review the README.md file
- Check example code in the `example` package
- Review RocketMQ documentation: https://rocketmq.apache.org/

## Version History

### 1.0.0 (Initial Release)
- Auto-generated topic and consumer group from service name
- Support for delayed messages (18 delay levels)
- Flexible parameter conversion across different packages
- Full async message support with callbacks
- Spring Boot auto-configuration
- Non-invasive configuration approach
