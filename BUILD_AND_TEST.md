# 幂等性功能修复 - 构建和测试指南

## 修复内容总结

### 问题1：IdempotentProcessor 未注入（已修复）
- **文件**：`MdpServerClassRegister.java`
- **问题**：创建 `MdpMessageListener` 时没有传入 `IdempotentProcessor`
- **修复**：注入并传递 `IdempotentProcessor`

### 问题2：StringRedisTemplate 缺失（已修复）
- **文件**：`mdp-example/src/main/java/com/gaoji/common/mdp/example/config/RedisConfig.java`
- **问题**：只配置了 `RedisTemplate<String, Object>`，缺少幂等性需要的 `StringRedisTemplate`
- **修复**：添加 `stringRedisTemplate()` Bean

### 问题3：SpEL 参数名解析失败（已修复）
- **文件**：`IdempotentProcessor.java` 和 `mdp-example/pom.xml`
- **问题**：编译器未保留参数名，导致 `#order.orderId` 表达式无法解析
- **修复**：
  1. **pom.xml**：添加 `-parameters` 编译参数（推荐方案）
  2. **IdempotentProcessor**：智能降级逻辑，从表达式中提取期望的变量名（兼容方案）

---

## 构建步骤

### 1. 清理并重新编译

```bash
cd E:\IdeaProjects\gaoji-common-mdp-enhanced

# 清理旧的编译结果
mvn clean

# 编译父模块（gaoji-common-mdp-enhanced）
mvn compile install -DskipTests

# 编译示例模块（mdp-example）
cd mdp-example
mvn clean compile
```

### 2. 验证参数名是否保留

```bash
# 检查编译后的字节码
javap -v target/classes/com/gaoji/common/mdp/example/server/OrderMdpServer.class | grep -A 10 "sendOrder"
```

**期望输出**（包含 `MethodParameters` 段）：
```
public void sendOrder(com.gaoji.common.mdp.example.domain.OrderInfo);
  descriptor: (Lcom/gaoji/common/mdp/example/domain/OrderInfo;)V
  flags: ACC_PUBLIC
  MethodParameters:
    Name                           Flags
    order                          
```

---

## 测试步骤

### 3. 启动 Redis（如果未启动）

```bash
# Windows (假设 Redis 安装在默认路径)
redis-server

# 或者使用 Docker
docker run -d -p 6379:6379 redis:latest
```

### 4. 启动 RocketMQ NameServer 和 Broker（如果未启动）

```bash
# 启动 NameServer
start mqnamesrv.cmd

# 启动 Broker
start mqbroker.cmd -n localhost:9876
```

### 5. 运行测试

```bash
cd E:\IdeaProjects\gaoji-common-mdp-enhanced\mdp-example

# 运行所有测试
mvn test

# 或运行特定测试
mvn test -Dtest=MdpExampleTest
```

---

## 测试验证要点

### ✅ 测试1：不同订单正常消费
- **测试方法**：`testDifferentOrdersConsume()`
- **预期结果**：
  - 发送10条不同订单
  - 全部被正常处理
  - 日志中看到10次 "订单处理成功"
  - **不再出现** "Property or field 'orderId' cannot be found on null" 错误

### ✅ 测试2：相同订单幂等性拦截
- **测试方法**：`testSameOrderIdempotentConsume()`
- **预期结果**：
  - 发送同一订单ID的消息（如 `ORDER_IDEMPOTENT_001`）
  - 第1次处理成功，日志显示 "订单处理成功"
  - 后续重复消息被拦截，日志显示 "消息已处理，跳过重复消费"
  - **不再进入业务处理逻辑**

### ✅ 测试3：并发场景幂等性
- **测试方法**：`testConcurrentIdempotentConsume()`
- **预期结果**：
  - 10个线程并发发送相同订单ID
  - 只有第1个消息被处理
  - 其他9个消息被幂等性拦截
  - Redis 中只有1条处理记录

---

## 预期日志示例

### ✅ 正常日志（修复后）

```
2026-09-21 13:04:07.004 [ConsumeMessageThread_1] INFO  c.g.c.m.e.server.OrderMdpServer - 收到异步订单消息: OrderInfo{orderId='ORDER_DIFFERENT_001', ...}
2026-09-21 13:04:07.004 [ConsumeMessageThread_1] INFO  c.g.c.m.e.server.OrderMdpServer - 开始处理订单 [异步消息]: orderId=ORDER_DIFFERENT_001, ...
2026-09-21 13:04:07.004 [ConsumeMessageThread_1] INFO  c.g.c.m.e.server.OrderMdpServer - 订单处理成功 [异步消息]: orderId=ORDER_DIFFERENT_001
2026-09-21 13:04:07.005 [ConsumeMessageThread_1] DEBUG c.g.c.m.e.c.MdpMessageListener - 消息处理成功 - MsgId: 7F00000127F8..., Method: sendOrderAsync
```

### ✅ 幂等性拦截日志（修复后）

```
2026-09-21 13:04:07.150 [ConsumeMessageThread_2] DEBUG c.g.c.m.e.c.MdpMessageListener - 接收消息 - Topic: order_service, MsgId: 7F00000127F8...
2026-09-21 13:04:07.151 [ConsumeMessageThread_2] INFO  c.g.c.m.e.i.IdempotentProcessor - 消息已处理，跳过重复消费 - method: sendOrderAsync, businessKey: ORDER_IDEMPOTENT_001
2026-09-21 13:04:07.151 [ConsumeMessageThread_2] DEBUG c.g.c.m.e.c.MdpMessageListener - 消息处理成功 - MsgId: 7F00000127F8..., Method: sendOrderAsync
```

注意：**不再有业务逻辑执行日志**（"收到订单消息"、"开始处理订单"、"订单处理成功"）

### ❌ 错误日志（修复前）

```
2026-09-21 11:35:30.100 [ConsumeMessageThread_3] ERROR c.g.c.m.e.i.IdempotentProcessor - 提取业务唯一键失败 - expression: #order.orderId, parameter: OrderInfo{...}
org.springframework.expression.spel.SpelEvaluationException: EL1007E: Property or field 'orderId' cannot be found on null
2026-09-21 11:35:30.101 [ConsumeMessageThread_3] ERROR c.g.c.m.e.i.IdempotentProcessor - 无法提取业务唯一键 - method: sendOrderAsync, expression: #order.orderId
```

---

## 故障排查

### 问题1：仍然报 "Property or field 'orderId' cannot be found on null"

**原因**：Maven 编译时未使用新配置

**解决**：
```bash
# 完全清理并重新编译
mvn clean
mvn compile
```

**或者在 IDEA 中**：
1. `Build` → `Rebuild Project`
2. 确保 IDEA 的 Compiler 设置中也启用了 `-parameters`

### 问题2：幂等性不生效（重复消息都被处理）

**检查项**：

1. **Redis 是否启动**
   ```bash
   redis-cli ping
   # 应返回 PONG
   ```

2. **Redis 配置是否正确**
   ```bash
   # 检查 application.properties
   spring.redis.host=localhost
   spring.redis.port=6379
   ```

3. **查看日志中是否有幂等性相关日志**
   ```
   [IdempotentProcessor] - 检查幂等性 - method: xxx, businessKey: xxx
   ```

### 问题3：编译错误

**错误信息**：`Cannot resolve method 'extractVariableFromExpression'`

**原因**：IDE 未刷新

**解决**：
1. IDEA: `File` → `Invalidate Caches / Restart`
2. 或者运行 `mvn clean compile`

---

## 下一步

修复完成后，建议：

1. ✅ 提交代码到 Git
2. ✅ 更新项目文档，说明需要配置 `-parameters` 编译参数
3. ✅ 在 CI/CD 中确保使用了正确的 Maven 配置
4. ✅ 编写用户指南，说明如何使用幂等性功能

---

## 相关文档

- `IDEMPOTENT_FIX.md` - 幂等性功能修复总结（问题1和2）
- `SPEL_PARAMETER_FIX.md` - SpEL 参数名解析问题详解（问题3）

---

修复日期：2026-09-21
