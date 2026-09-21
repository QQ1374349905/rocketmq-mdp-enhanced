package com.gaoji.common.mdp.enhanced.idempotent;

import com.gaoji.common.mdp.enhanced.annotation.Idempotent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;

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
     */
    private String extractBusinessKey(String expression, Method method, Object parameter) {
        try {
            EvaluationContext context = new StandardEvaluationContext();

            if (method.getParameterCount() > 0) {
                String paramName = getParameterName(method, 0);
                context.setVariable(paramName, parameter);
            }

            Object value = parser.parseExpression(expression).getValue(context);
            return value != null ? value.toString() : null;

        } catch (Exception e) {
            log.error("提取业务唯一键失败 - expression: {}, parameter: {}", expression, parameter, e);
            return null;
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
     * 重复消息异常
     */
    public static class DuplicateMessageException extends RuntimeException {
        public DuplicateMessageException(String message) {
            super(message);
        }
    }
}
