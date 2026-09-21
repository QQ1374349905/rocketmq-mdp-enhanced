# RocketMQ MDP 增强版 - 优化说明文档

## 📋 本次优化内容

本次优化主要解决了三个问题：

1. ✅ **修复测试框架注解混用问题**
2. ✅ **添加Redis连接池优化**
3. ✅ **添加幂等性记录TTL配置**

---

## 1️⃣ 修复测试框架注解混用问题

### 问题描述
```java
// ❌ 错误：JUnit 4 和 JUnit 5 注解混用
@RunWith(SpringRunner.class)  // JUnit 4
@Test                         // JUnit 5
```

导致测试报错：
```
org.junit.runners.model.InvalidTestClassError: Invalid test class
  1. No runnable methods
```

### 解决方案

**文件：** `mdp-example/src/test/java/com/example/mdp/MdpExampleTest.java`

```java
// ✅ 正确：统一使用 JUnit 5
@ExtendWith(SpringExtension.class)  // JUnit 5
@SpringBootTest(classes = MdpExampleApplication.class)
public class MdpExampleTest {
    @Test  // JUnit 5
    public void testSendOrderSync() {
        // ...
    }
}
```

### 修改内容
- 删除：`import org.junit.runner.RunWith;`
- 删除：`import org.springframework.test.context.junit4.SpringRunner;`
- 新增：`import org.junit.jupiter.api.extension.ExtendWith;`
- 新增：`import org.springframework.test.context.junit.jupiter.SpringExtension;`
- 修改：`@RunWith(SpringRunner.class)` → `@ExtendWith(SpringExtension.class)`

---

## 2️⃣ Redis连接池优化

### 问题描述
从日志中发现每次幂等性检查需要3-4次Redis连接：
```
Opening RedisConnection → Closing Redis Connection (检查锁)
Opening RedisConnection → Closing Redis Connection (检查幂等)
Opening RedisConnection → Closing Redis Connection (记录幂等)
Opening RedisConnection → Closing Redis Connection (释放锁)
```

频繁的连接创建和关闭影响性能。

### 优化方案

#### 2.1 优化连接池配置

**文件：** `mdp-example/src/main/resources/application.yml`

```yaml
spring:
  redis:
    lettuce:
      pool:
        max-active: 16      # 从8增加到16，支持更高并发
        max-idle: 8         # 保持不变
        min-idle: 2         # 从0增加到2，保持热连接
        max-wait: 3000ms    # 从-1改为3秒，避免无限等待
      shutdown-timeout: 100ms
```

**优化说明：**
- `max-active: 16`：支持更多并发消费线程
- `min-idle: 2`：预创建2个空闲连接，避免冷启动
- `max-wait: 3000ms`：避免死锁时无限等待

#### 2.2 使用Lua脚本减少网络往返

**文件：** `src/main/java/com/rocketmq/mdp/enhanced/idempotent/RedisIdempotentService.java`

**优化前：** 4次Redis调用
```java
// 1. 获取锁
setIfAbsent(lockKey, lockValue)
// 2. 检查幂等
get(idempotentKey)
// 3. 记录幂等
set(idempotentKey, messageId)
// 4. 释放锁
execute(releaseLockScript)
```

**优化后：** 1次Redis调用（Lua脚本）
```java
String luaScript =
    "local lockKey = KEYS[1] " +
    "local idempotentKey = KEYS[2] " +
    "-- 1. 获取锁 " +
    "local lockAcquired = redis.call('SET', lockKey, lockValue, 'NX', 'EX', lockTimeout) " +
    "if not lockAcquired then return {0, 'lock_failed', ''} end " +
    "-- 2. 检查幂等 " +
    "local existingMessageId = redis.call('GET', idempotentKey) " +
    "if existingMessageId then " +
    "    redis.call('DEL', lockKey) " +
    "    return {0, 'duplicate', existingMessageId} " +
    "end " +
    "-- 3. 记录幂等 " +
    "redis.call('SET', idempotentKey, messageId, 'EX', idempotentTimeout) " +
    "-- 4. 释放锁 " +
    "redis.call('DEL', lockKey) " +
    "return {1, 'success', ''}";
```

**性能提升：**
- 网络往返：4次 → 1次（减少75%）
- 原子性保证：避免并发竞态条件
- 锁持有时间：大幅缩短

---

## 3️⃣ 幂等性记录TTL配置

### 问题描述
从测试日志发现：之前测试的订单都被检测为重复消息，说明Redis中的幂等性记录**一直存在**，没有过期。

```
⚠️ 检测到重复消息 - businessKey: ORDER001
⚠️ 检测到重复消息 - businessKey: ORDER_BATCH_001
⚠️ 检测到重复消息 - businessKey: ORDER_CONCURRENT_001
```

如果不配置TTL，Redis会无限增长。

### 解决方案

#### 3.1 创建配置属性类

**文件：** `src/main/java/com/rocketmq/mdp/enhanced/config/IdempotentProperties.java`

```java
@ConfigurationProperties(prefix = "mdp.idempotent")
public class IdempotentProperties {
    
    /**
     * 幂等性记录过期时间（秒）
     * 默认：24小时（86400秒）
     */
    private long ttl = 86400L;
    
    /**
     * 分布式锁超时时间（秒）
     * 默认：10秒
     */
    private long lockTimeout = 10L;
    
    /**
     * 是否启用幂等性（默认启用）
     */
    private boolean enabled = true;
    
    // getter/setter...
}
```

#### 3.2 更新配置类

**文件：** `src/main/java/com/rocketmq/mdp/enhanced/config/IdempotentConfig.java`

```java
@Configuration
@EnableScheduling
@EnableConfigurationProperties(IdempotentProperties.class)  // 启用配置属性
public class IdempotentConfig {
    
    @Bean
    public IdempotentService redisIdempotentService(
            StringRedisTemplate stringRedisTemplate,
            IdempotentProperties properties) {  // 注入配置
        
        RedisIdempotentService service = new RedisIdempotentService(stringRedisTemplate);
        service.setDefaultTtl(properties.getTtl());
        service.setDefaultLockTimeout(properties.getLockTimeout());
        
        return service;
    }
}
```

#### 3.3 更新Redis服务

**文件：** `src/main/java/com/rocketmq/mdp/enhanced/idempotent/RedisIdempotentService.java`

```java
public class RedisIdempotentService implements IdempotentService {
    
    /**
     * 默认TTL（秒）- 可通过配置修改
     */
    private long defaultTtl = 86400L; // 24小时
    
    /**
     * 默认锁超时（秒）- 可通过配置修改
     */
    private long defaultLockTimeout = 10L; // 10秒
    
    public void setDefaultTtl(long defaultTtl) {
        this.defaultTtl = defaultTtl;
    }
    
    public void setDefaultLockTimeout(long defaultLockTimeout) {
        this.defaultLockTimeout = defaultLockTimeout;
    }
    
    @Override
    public boolean tryProcess(String businessKey, String messageId, long timeout, long lockTimeout) {
        // 使用配置的默认值（如果参数未指定）
        long actualTimeout = timeout > 0 ? timeout : defaultTtl;
        long actualLockTimeout = lockTimeout > 0 ? lockTimeout : defaultLockTimeout;
        
        // Lua脚本中使用actualTimeout设置过期时间
        // ...
    }
}
```

#### 3.4 应用配置

**文件：** `mdp-example/src/main/resources/application.yml`

```yaml
# MDP 增强版配置
mdp:
  idempotent:
    enabled: true        # 是否启用幂等性（默认true）
    ttl: 86400           # 幂等性记录过期时间（秒），默认24小时
    lock-timeout: 10     # 分布式锁超时时间（秒），默认10秒
```

---

## 📊 优化效果对比

| 优化项 | 优化前 | 优化后 | 提升 |
|--------|--------|--------|------|
| **测试框架** | JUnit 4/5混用，测试报错 | 统一JUnit 5，正常运行 | 100% |
| **Redis网络往返** | 4次/消息 | 1次/消息 | 75% ↓ |
| **连接池最大连接数** | 8 | 16 | 100% ↑ |
| **连接池最小空闲** | 0（冷启动） | 2（热连接） | 性能提升 |
| **Redis空间增长** | 无限增长 | 24小时自动清理 | 避免OOM |
| **锁持有时间** | 长（多次调用） | 短（原子操作） | 并发性提升 |

---

## 🎯 使用建议

### 1. TTL配置建议

根据业务场景调整TTL：

| 业务类型 | 推荐TTL | 说明 |
|---------|---------|------|
| 订单类 | 24-72小时 | 用户可能短期内重复下单 |
| 支付类 | 7-30天 | 支付记录需要更长保留 |
| 日志类 | 1-7天 | 日志去重时间较短 |
| 库存扣减 | 1小时 | 高频操作，短期去重 |

**配置示例：**
```yaml
mdp:
  idempotent:
    ttl: 259200  # 3天（72小时）
```

### 2. 连接池配置建议

根据消费线程数调整：

| 消费线程数 | max-active | min-idle |
|-----------|------------|----------|
| 10以下 | 8 | 2 |
| 10-20 | 16 | 4 |
| 20-50 | 32 | 8 |
| 50以上 | 64 | 16 |

**配置示例：**
```yaml
spring:
  redis:
    lettuce:
      pool:
        max-active: 32   # 支持20-50个消费线程
        min-idle: 8
```

### 3. 锁超时配置建议

根据消费耗时调整：

| 消费耗时 | lock-timeout |
|---------|--------------|
| <1秒（快速消费） | 5秒 |
| 1-5秒（正常消费） | 10秒 |
| 5-30秒（慢速消费） | 30秒 |
| >30秒（超慢消费） | 60秒 |

**配置示例：**
```yaml
mdp:
  idempotent:
    lock-timeout: 30  # 支持慢速消费
```

---

## 🔍 验证优化效果

### 1. 验证测试框架修复

```bash
# 运行测试，不再报错
mvn test -Dtest=MdpExampleTest
```

**预期结果：**
```
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
```

### 2. 验证Redis连接优化

**启动应用并观察日志：**
```bash
# 不再看到频繁的 Opening/Closing Redis Connection
# 日志级别已改为INFO，减少干扰
```

### 3. 验证TTL配置

**发送测试消息后检查Redis：**
```bash
# 连接Redis
redis-cli

# 查看幂等性记录
KEYS mdp:idempotent:*

# 查看某个记录的TTL（秒）
TTL mdp:idempotent:ORDER001
# 输出：86399（约24小时）

# 24小时后再次查看
TTL mdp:idempotent:ORDER001
# 输出：-2（已过期自动删除）
```

---

## 📚 相关文件清单

### 修改的文件

1. **测试类**
   - `mdp-example/src/test/java/com/example/mdp/MdpExampleTest.java`

2. **配置类**
   - `src/main/java/com/rocketmq/mdp/enhanced/config/IdempotentConfig.java`
   - `src/main/java/com/rocketmq/mdp/enhanced/config/IdempotentProperties.java`（新增）

3. **服务类**
   - `src/main/java/com/rocketmq/mdp/enhanced/idempotent/RedisIdempotentService.java`

4. **配置文件**
   - `mdp-example/src/main/resources/application.yml`

### 新增的文件

1. `src/main/java/com/rocketmq/mdp/enhanced/config/IdempotentProperties.java`
2. `OPTIMIZATION.md`（本文档）

---

## ⚠️ 注意事项

### 1. 向后兼容性

所有配置都有**默认值**，不影响现有应用：
- `ttl: 86400`（24小时）
- `lock-timeout: 10`（10秒）
- `enabled: true`（启用）

### 2. 升级步骤

```bash
# 1. 拉取最新代码
git pull

# 2. 重新编译
mvn clean install

# 3. 可选：添加配置（使用默认值可跳过）
# 编辑 application.yml，添加 mdp.idempotent 配置

# 4. 重启应用
```

### 3. 监控建议

**监控Redis内存使用：**
```bash
redis-cli INFO memory | grep used_memory_human
```

**监控幂等性记录数量：**
```bash
redis-cli KEYS "mdp:idempotent:*" | wc -l
```

**监控连接池状态：**
- 通过Spring Boot Actuator的 `/actuator/metrics` 端点
- 指标：`lettuce.pool.active`、`lettuce.pool.idle`

---

## 🎉 总结

本次优化从三个方面提升了系统的**可靠性、性能和可维护性**：

1. ✅ **修复测试框架** - 确保测试正常运行
2. ✅ **优化Redis连接** - 减少75%网络往返，提升并发能力
3. ✅ **添加TTL配置** - 避免Redis无限增长，支持灵活配置

所有优化**向后兼容**，不影响现有代码。建议在生产环境根据实际业务场景调整配置参数。

---

**文档版本：** 1.0  
**更新日期：** 2026-09-21  
**作者：** RocketMQ MDP Enhanced Team
