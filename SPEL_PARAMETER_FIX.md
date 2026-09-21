# SpEL 参数名解析问题修复

## 问题描述

幂等性功能启用后，SpEL 表达式解析失败：

```
org.springframework.expression.spel.SpelEvaluationException: EL1007E: Property or field 'orderId' cannot be found on null
```

错误日志显示：
```
ERROR c.g.c.m.e.i.IdempotentProcessor - 提取业务唯一键失败 - expression: #order.orderId, parameter: OrderInfo{...}
ERROR c.g.c.m.e.i.IdempotentProcessor - 无法提取业务唯一键 - method: sendOrderAsync, expression: #order.orderId
```

## 根本原因

Java 编译器默认**不保留参数名信息**。编译后的字节码中，参数名变成 `arg0`、`arg1` 等。

当 `IdempotentProcessor` 使用 `method.getParameters()[0].getName()` 时：
- ❌ 没有 `-parameters` 编译参数 → 返回 `arg0`
- ✅ 有 `-parameters` 编译参数 → 返回 `order`

SpEL 上下文中注册的变量名是 `arg0`，但表达式 `#order.orderId` 期望的变量名是 `order`，导致解析失败。

## 修复方案

### 方案1：配置 Maven 保留参数名（推荐）

**修改文件**：`mdp-example/pom.xml`

**改动**：添加 `maven-compiler-plugin` 配置：

```xml
<build>
    <plugins>
        <!-- Maven Compiler Plugin: 保留参数名以支持 SpEL 表达式 -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.8.1</version>
            <configuration>
                <source>${java.version}</source>
                <target>${java.version}</target>
                <encoding>${project.build.sourceEncoding}</encoding>
                <!-- 保留参数名，用于 @Idempotent 注解的 SpEL 表达式解析 -->
                <compilerArgs>
                    <arg>-parameters</arg>
                </compilerArgs>
            </configuration>
        </plugin>
        ...
    </plugins>
</build>
```

**优点**：
- ✅ 标准解决方案
- ✅ 支持所有参数名
- ✅ 代码可读性好

**缺点**：
- ⚠️ 需要重新编译
- ⚠️ 字节码稍微增大（通常可忽略）

---

### 方案2：智能降级支持（已实现）

**修改文件**：`src/main/java/com/gaoji/common/mdp/enhanced/idempotent/IdempotentProcessor.java`

**改动**：增强 SpEL 上下文设置逻辑：

```java
// 使用 SpEL 表达式提取业务键
StandardEvaluationContext context = new StandardEvaluationContext();

if (method.getParameterCount() > 0) {
    // 获取参数名（可能是真实名称如 "order"，也可能是编译后的 "arg0"）
    String paramName = getParameterName(method, 0);
    context.setVariable(paramName, parameter);

    // 如果是 argN 格式，尝试从表达式中提取期望的参数名
    // 例如：表达式 "#order.orderId" 期望参数名是 "order"
    if (paramName.startsWith("arg") && expression.startsWith("#")) {
        String expectedParamName = extractVariableFromExpression(expression);
        if (expectedParamName != null && !expectedParamName.equals(paramName)) {
            // 同时注册期望的参数名，兼容编译时未保留参数名的情况
            context.setVariable(expectedParamName, parameter);
            log.debug("参数名降级 - 编译名: {}, 期望名: {}", paramName, expectedParamName);
        }
    }
}
```

**新增方法**：从表达式中提取变量名

```java
/**
 * 从 SpEL 表达式中提取变量名
 * 例如：#order.orderId -> order
 *       #user.id -> user
 */
private String extractVariableFromExpression(String expression) {
    try {
        if (expression == null || !expression.startsWith("#")) {
            return null;
        }

        // 移除开头的 #
        String withoutHash = expression.substring(1);

        // 找到第一个 . 的位置
        int dotIndex = withoutHash.indexOf('.');
        if (dotIndex > 0) {
            return withoutHash.substring(0, dotIndex);
        }

        // 没有 . 则返回整个变量名
        return withoutHash;
    } catch (Exception e) {
        return null;
    }
}
```

**优点**：
- ✅ 无需重新编译现有代码
- ✅ 向后兼容
- ✅ 自动降级

**缺点**：
- ⚠️ 只支持简单表达式（`#varName.property`）
- ⚠️ 不支持复杂表达式（如 `#order.items[0].id`）

---

## 推荐实践

**同时使用两个方案**：

1. **新项目**：在 `pom.xml` 中配置 `-parameters` 编译参数
2. **框架层**：实现智能降级逻辑，兼容未配置的老项目

这样可以保证：
- 新项目有最佳体验
- 老项目不会因为未配置而报错

---

## 验证方法

### 检查是否保留了参数名

```bash
# 编译项目
mvn clean compile

# 查看字节码中的参数名
javap -v target/classes/com/gaoji/common/mdp/example/server/OrderMdpServer.class | grep -A 5 "sendOrder"
```

**没有 `-parameters`**：
```
public void sendOrder(com.gaoji.common.mdp.example.domain.OrderInfo);
  descriptor: (Lcom/gaoji/common/mdp/example/domain/OrderInfo;)V
  flags: ACC_PUBLIC
  Code:
    ...
```

**有 `-parameters`**：
```
public void sendOrder(com.gaoji.common.mdp.example.domain.OrderInfo);
  descriptor: (Lcom/gaoji/common/mdp/example/domain/OrderInfo;)V
  flags: ACC_PUBLIC
  MethodParameters:
    Name                           Flags
    order                          
  Code:
    ...
```

### 运行测试

```bash
mvn clean test -Dtest=MdpExampleTest
```

**预期结果**：
- ✅ 不同订单全部处理成功
- ✅ 相同订单只处理一次，其他被幂等性拦截
- ❌ 不再出现 "Property or field 'orderId' cannot be found on null" 错误

---

## 注意事项

1. **IDEA 用户**：确保 IDEA 也配置了 `-parameters`
   - `File` → `Settings` → `Build, Execution, Deployment` → `Compiler` → `Java Compiler`
   - 勾选 `Add runtime assertions for not-null-annotated methods and parameters`
   - 在 `Additional command line parameters` 中添加 `-parameters`

2. **生产部署**：确保 CI/CD 构建脚本中包含 `-parameters` 配置

3. **性能影响**：`-parameters` 会略微增加字节码大小（通常 < 1%），但不影响运行时性能

---

## 相关文件

- **修复文件1**：`mdp-example/pom.xml` - 添加编译器配置
- **修复文件2**：`IdempotentProcessor.java` - 智能降级逻辑
- **相关文档**：`IDEMPOTENT_FIX.md` - 幂等性功能修复总结

---

修复日期：2026-09-21
