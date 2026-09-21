package com.gaoji.common.mdp.enhanced.idempotent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gaoji.common.mdp.enhanced.annotation.Idempotent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final IdempotentService idempotentService;

    public IdempotentProcessor(IdempotentService idempotentService) {
        this.idempotentService = idempotentService;
    }

    /**
     * 检查方法是否需要幂等性保证
     */
    public boolean needsIdempotent(Method method) {
        Idempotent idempotent = method.getAnnotation(Idempotent.class);
        return idempotent != null && idempotent.enabled();
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
        if (idempotent == null || !idempotent.enabled()) {
            return true;
        }

        try {
            String businessKey = extractBusinessKey(idempotent.key(), method, parameter);
            if (businessKey == null || businessKey.isEmpty()) {
                log.error("无法提取业务唯一键 - method: {}, key: {}",
                        method.getName(), idempotent.key());
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
            String businessKey = extractBusinessKey(idempotent.key(), method, parameter);
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
            String businessKey = extractBusinessKey(idempotent.key(), method, parameter);
            if (businessKey != null && !businessKey.isEmpty()) {
                idempotentService.markFailed(businessKey, messageId);
            }
        } catch (Exception e) {
            log.error("标记消息失败失败 - method: {}, messageId: {}", method.getName(), messageId, e);
        }
    }

    /**
     * 从参数中提取业务唯一键
     *
     * 策略：
     * 1. 如果未指定 key，使用参数的 MD5 值（默认）
     * 2. 如果指定了 key，通过反射调用 getter 方法提取
     * 3. 如果反射失败，降级使用 MD5
     *
     * @param key 字段路径，如 "orderId" 或 "user.userId"
     * @param method 消费者方法
     * @param parameter 方法参数
     * @return 业务唯一键
     */
    private String extractBusinessKey(String key, Method method, Object parameter) {
        try {
            // 1. 未指定 key，使用 MD5（默认策略）
            if (key == null || key.trim().isEmpty()) {
                return calculateMd5(parameter);
            }

            // 2. 通过反射提取字段值
            Object value = extractValueByPath(parameter, key);
            if (value != null) {
                return value.toString();
            }

            // 3. 提取失败，降级到 MD5
            log.warn("字段路径提取失败，降级使用 MD5 - method: {}, key: {}, parameterType: {}",
                    method.getName(), key, parameter.getClass().getSimpleName());
            return calculateMd5(parameter);

        } catch (Exception e) {
            log.error("提取业务唯一键失败，降级使用 MD5 - method: {}, key: {}, error: {}",
                    method.getName(), key, e.getMessage());
            return calculateMd5(parameter);
        }
    }

    /**
     * 通过字段路径提取值
     *
     * 支持：
     * - 简单路径：orderId → getOrderId()
     * - 嵌套路径：user.userId → getUser().getUserId()
     * - 多层嵌套：order.user.id → getOrder().getUser().getId()
     *
     * @param obj 对象
     * @param path 字段路径，用 "." 分隔
     * @return 字段值，失败返回 null
     */
    private Object extractValueByPath(Object obj, String path) {
        if (obj == null || path == null || path.trim().isEmpty()) {
            return null;
        }

        try {
            Object current = obj;
            String[] parts = path.split("\\.");

            for (String part : parts) {
                if (current == null) {
                    return null;
                }

                // 构造 getter 方法名：orderId → getOrderId
                String methodName = "get" + capitalize(part);

                // 查找并调用 getter 方法
                Method getter = current.getClass().getMethod(methodName);
                current = getter.invoke(current);
            }

            return current;

        } catch (Exception e) {
            log.debug("字段路径提取失败 - path: {}, objectType: {}, error: {}",
                    path, obj.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * 首字母大写
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
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
     * 重复消息异常
     */
    public static class DuplicateMessageException extends RuntimeException {
        public DuplicateMessageException(String message) {
            super(message);
        }
    }
}
