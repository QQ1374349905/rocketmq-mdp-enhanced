package com.gaoji.common.mdp.enhanced.converter;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * 灵活参数转换器
 * 支持不同包名、不同类之间的自动转换
 *
 * 转换策略：
 * 1. JSON序列化/反序列化（优先）
 * 2. 字段映射（备选）
 * 3. 类型强制转换（兜底）
 */
public class ParameterConverter {

    private static final Logger log = LoggerFactory.getLogger(ParameterConverter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        // 忽略未知属性
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 将源对象转换为目标类型
     *
     * @param source 源对象
     * @param targetType 目标类型
     * @return 转换后的对象
     */
    public static <T> T convert(Object source, Class<T> targetType) {
        if (source == null) {
            return null;
        }

        // 如果类型相同，直接返回
        if (targetType.isAssignableFrom(source.getClass())) {
            return targetType.cast(source);
        }

        try {
            // 策略1: JSON序列化/反序列化
            String json = objectMapper.writeValueAsString(source);
            return objectMapper.readValue(json, targetType);
        } catch (Exception e1) {
            log.warn("JSON转换失败，尝试字段映射。源类型：{}，目标类型：{}，错误：{}",
                    source.getClass().getName(), targetType.getName(), e1.getMessage());

            try {
                // 策略2: 字段映射
                return convertByFieldMapping(source, targetType);
            } catch (Exception e2) {
                log.error("字段映射转换失败。源类型：{}，目标类型：{}，错误：{}",
                        source.getClass().getName(), targetType.getName(), e2.getMessage());
                throw new RuntimeException("参数转换失败", e2);
            }
        }
    }

    /**
     * 通过字段映射转换
     */
    private static <T> T convertByFieldMapping(Object source, Class<T> targetType) throws Exception {
        T target = targetType.newInstance();

        Map<String, Field> sourceFields = getAllFields(source.getClass());
        Map<String, Field> targetFields = getAllFields(targetType);

        for (Map.Entry<String, Field> entry : targetFields.entrySet()) {
            String fieldName = entry.getKey();
            Field targetField = entry.getValue();
            Field sourceField = sourceFields.get(fieldName);

            if (sourceField != null) {
                sourceField.setAccessible(true);
                targetField.setAccessible(true);

                Object value = sourceField.get(source);
                if (value != null) {
                    // 类型兼容性检查
                    if (targetField.getType().isAssignableFrom(sourceField.getType())) {
                        targetField.set(target, value);
                    } else {
                        // 尝试类型转换
                        Object convertedValue = convertValue(value, targetField.getType());
                        targetField.set(target, convertedValue);
                    }
                }
            }
        }

        return target;
    }

    /**
     * 获取所有字段（包括父类）
     */
    private static Map<String, Field> getAllFields(Class<?> clazz) {
        Map<String, Field> fields = new HashMap<>();
        Class<?> current = clazz;

        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (!fields.containsKey(field.getName())) {
                    fields.put(field.getName(), field);
                }
            }
            current = current.getSuperclass();
        }

        return fields;
    }

    /**
     * 值类型转换
     */
    private static Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }

        // String转换
        if (targetType == String.class) {
            return value.toString();
        }

        String strValue = value.toString();

        // 基本类型和包装类型
        if (targetType == Integer.class || targetType == int.class) {
            return Integer.parseInt(strValue);
        }
        if (targetType == Long.class || targetType == long.class) {
            return Long.parseLong(strValue);
        }
        if (targetType == Double.class || targetType == double.class) {
            return Double.parseDouble(strValue);
        }
        if (targetType == Float.class || targetType == float.class) {
            return Float.parseFloat(strValue);
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            return Boolean.parseBoolean(strValue);
        }
        if (targetType == Short.class || targetType == short.class) {
            return Short.parseShort(strValue);
        }
        if (targetType == Byte.class || targetType == byte.class) {
            return Byte.parseByte(strValue);
        }

        // 复杂类型，尝试JSON转换
        try {
            String json = objectMapper.writeValueAsString(value);
            return objectMapper.readValue(json, targetType);
        } catch (Exception e) {
            throw new RuntimeException("无法转换 " + value.getClass() + " 到 " + targetType, e);
        }
    }
}
