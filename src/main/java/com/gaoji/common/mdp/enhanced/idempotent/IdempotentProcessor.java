package com.gaoji.common.mdp.enhanced.idempotent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gaoji.common.mdp.enhanced.annotation.Idempotent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 幂等性处理器
 *
 * 负责：
 * 1. 解析 @Idempotent 注解
 * 2. 从消息参数中提取业务唯一键
 * 3. 执行幂等性检查
 * 4. 处理重复消息
 */
public class IdempotentProcessor {

    private static final Logger log = LoggerFactory.getLogger(IdempotentProcessor.class);
    private static final ExpressionParser parser = new SpelExpressionParser();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final IdempotentService idempotentService;

    public IdempotentProcessor(IdempotentService idempotentService) {
        this.idempotentService = idempotentService;
    }

    /**
     * 检查方法是否需要幂等性保证
     */
    public boolean needsIdempotent(Method method) {
        return method.isAnnotationPresent(Idempotent.class);
    }

    /**
     * 执行幂等性检查
     *
     * @param method 消费者方法
     * @param parameter 方法参数
     * @param messageId RocketMQ消息ID
     * @return true=允许处理，false=重复消息
     */
    public boolean checkIdempotent(Method method, Object parameter, String messageId) {
        Idempotent idempotent = method.getAnnotation(Idempotent.class);
        if (idempotent == null) {
            return true;
        }

        try {
            String businessKey = extractBusinessKey(idempotent.keyExpression(), method, parameter);
            if (businessKey == null || businessKey.isEmpty()) {
                log.error("无法提取业务唯一键 - method: {}, expression: {}",
                        method.getName(), idempotent.keyExpression());
                return true;
            }

            boolean canProcess = idempotentService.tryProcess(
                    businessKey,
                    messageId,
                    idempotent.timeout(),
                    idempotent.lockTimeout()
            );

            if (!canProcess) {
                log.warn("检测到重复消息 - businessKey: {}, messageId: {}, strategy: {}",
                        businessKey, messageId, idempotent.duplicateStrategy());

                if (idempotent.duplicateStrategy() == Idempotent.DuplicateStrategy.EXCEPTION) {
                    throw new DuplicateMessageException(
                            String.format("重复消息 - businessKey: %s, messageId: %s", businessKey, messageId)
                    );
                }
                return false;
            }

            log.debug("幂等性检查通过 - businessKey: {}, messageId: {}", businessKey, messageId);
            return true;

        } catch (DuplicateMessageException e) {
            throw e;
        } catch (Exception e) {
            log.error("幂等性检查失败，允许继续处理 - method: {}, messageId: {}",
                    method.getName(), messageId, e);
            return true;
        }
    }

    /**
     * 标记消息处理成功
     */
    public void markSuccess(Method method, Object parameter, String messageId) {
        Idempotent idempotent = method.getAnnotation(Idempotent.class);
        if (idempotent == null) {
            return;
        }

        try {
            String businessKey = extractBusinessKey(idempotent.keyExpression(), method, parameter);
            if (businessKey != null && !businessKey.isEmpty()) {
                idempotentService.markSuccess(businessKey, messageId);
            }
        } catch (Exception e) {
            log.error("标记消息成功失败 - method: {}, messageId: {}", method.getName(), messageId, e);
        }
    }

    /**
     * 标记消息处理失败
     */
    public void markFailed(Method method, Object parameter, String messageId) {
        Idempotent idempotent = method.getAnnotation(Idempotent.class);
        if (idempotent == null) {
            return;
        }

        try {
            String businessKey = extractBusinessKey(idempotent.keyExpression(), method, parameter);
            if (businessKey != null && !businessKey.isEmpty()) {
                idempotentService.markFailed(businessKey, messageId);
            }
        } catch (Exception e) {
            log.error("标记消息失败失败 - method: {}, messageId: {}", method.getName(), messageId, e);
        }
    }

    /**
     * 使用SpEL表达式从参数中提取业务唯一键
     * 如果未指定表达式，则使用参数的 MD5 值
     */
    private String extractBusinessKey(String expression, Method method, Object parameter) {
        try {
            // 如果未指定表达式，使用参数的 MD5 值
            if (expression == null || expression.trim().isEmpty()) {
                return calculateMd5(parameter);
            }

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

            Object value = parser.parseExpression(expression).getValue(context);
            return value != null ? value.toString() : null;

        } catch (Exception e) {
            log.error("提取业务唯一键失败 - expression: {}, parameter: {}", expression, parameter, e);
            return null;
        }
    }

    /**
     * 计算对象的 MD5 值
     * 使用 JSON 序列化来保证相同内容的对象生成相同的 MD5
     */
    private String calculateMd5(Object parameter) {
        try {
            String content;

            // 基本类型直接 toString
            if (parameter == null) {
                content = "null";
            } else if (parameter instanceof String ||
                       parameter instanceof Number ||
                       parameter instanceof Boolean) {
                content = parameter.toString();
            } else {
                // 对象类型使用 JSON 序列化
                content = objectMapper.writeValueAsString(parameter);
            }

            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            String md5 = hexString.toString();
            log.debug("计算参数 MD5 - type: {}, md5: {}",
                    parameter != null ? parameter.getClass().getSimpleName() : "null", md5);
            return md5;

        } catch (Exception e) {
            log.error("计算 MD5 失败 - parameter: {}", parameter, e);
            // 降级方案：使用类名 + hashCode（仅用于兜底，不保证内容相同）
            String fallback = (parameter != null ? parameter.getClass().getName() : "null") + ":" +
                            (parameter != null ? parameter.hashCode() : 0);
            log.warn("使用降级方案生成业务键 - fallback: {}", fallback);
            return String.valueOf(fallback.hashCode());
        }
    }

    /**
     * 获取参数名（简化版，实际应该从方法签名获取）
     */
    private String getParameterName(Method method, int index) {
        try {
            return method.getParameters()[index].getName();
        } catch (Exception e) {
            return "arg" + index;
        }
    }

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

    /**
     * 重复消息异常
     */
    public static class DuplicateMessageException extends RuntimeException {
        public DuplicateMessageException(String message) {
            super(message);
        }
    }
}
