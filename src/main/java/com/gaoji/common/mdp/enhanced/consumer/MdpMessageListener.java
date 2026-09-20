package com.gaoji.common.mdp.enhanced.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gaoji.common.mdp.enhanced.converter.ParameterConverter;
import com.gaoji.common.mdp.enhanced.domain.MdpMessage;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 增强版MDP消息监听器
 * 支持灵活参数转换和延迟消息处理
 * 支持根据消息中的方法名动态调用对应方法
 */
public class MdpMessageListener implements MessageListenerConcurrently {

    private static final Logger log = LoggerFactory.getLogger(MdpMessageListener.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final Object consumerBean;
    private final boolean flexibleConversion;

    public MdpMessageListener(Object consumerBean, Method handleMethod, boolean flexibleConversion) {
        this.consumerBean = consumerBean;
        this.flexibleConversion = flexibleConversion;
    }

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
        for (MessageExt msg : msgs) {
            try {
                String body = new String(msg.getBody(), StandardCharsets.UTF_8);
                log.debug("接收消息 - Topic: {}, MsgId: {}, Tags: {}",
                        msg.getTopic(), msg.getMsgId(), msg.getTags());

                // 解析增强消息
                MdpMessage enhancedMessage = objectMapper.readValue(body, MdpMessage.class);

                // 根据消息中的方法名查找对应的处理方法
                String methodName = enhancedMessage.getMethodName();
                if (methodName == null || methodName.isEmpty()) {
                    throw new IllegalStateException("消息中未包含方法名: " + msg.getMsgId());
                }

                Method handleMethod = findMethodByName(consumerBean.getClass(), methodName);
                if (handleMethod == null) {
                    throw new IllegalStateException(
                        String.format("消费者 %s 中未找到方法: %s",
                            consumerBean.getClass().getName(), methodName));
                }
                handleMethod.setAccessible(true);

                // 转换参数
                Object parameter = convertParameter(enhancedMessage, handleMethod);

                // 调用处理方法
                handleMethod.invoke(consumerBean, parameter);

                log.debug("消息处理成功 - MsgId: {}, Method: {}", msg.getMsgId(), methodName);
            } catch (Exception e) {
                log.error("消息处理失败 - Topic: {}, MsgId: {}, Error: {}",
                        msg.getTopic(), msg.getMsgId(), e.getMessage(), e);
                // 返回RECONSUME_LATER触发重试
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    /**
     * 根据方法名查找消费者类中的对应方法
     */
    private Method findMethodByName(Class<?> clazz, String methodName) {
        // 查找当前类中的方法
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() > 0) {
                return method;
            }
        }

        // 查找父类中的方法
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null && superClass != Object.class) {
            return findMethodByName(superClass, methodName);
        }

        return null;
    }

    /**
     * 转换消息参数到目标类型
     */
    private Object convertParameter(MdpMessage enhancedMessage, Method handleMethod) throws Exception {
        if (handleMethod.getParameterCount() == 0) {
            return null;
        }

        Class<?> targetType = handleMethod.getParameterTypes()[0];
        String bodyJson = enhancedMessage.getBody();

        if (bodyJson == null || bodyJson.isEmpty()) {
            return null;
        }

        // 首先尝试直接反序列化为目标类型
        try {
            return objectMapper.readValue(bodyJson, targetType);
        } catch (Exception e) {
            if (flexibleConversion) {
                // 如果失败且开启了灵活转换，先反序列化为原始类型，再转换
                log.debug("直接反序列化失败，尝试灵活转换。原始类型：{}，目标类型：{}",
                        enhancedMessage.getOriginalClassName(), targetType.getName());

                try {
                    // 先反序列化为Object或Map
                    Object sourceObj = objectMapper.readValue(bodyJson, Object.class);
                    // 使用灵活转换器转换
                    return ParameterConverter.convert(sourceObj, targetType);
                } catch (Exception e2) {
                    log.error("灵活转换失败", e2);
                    throw e2;
                }
            } else {
                throw e;
            }
        }
    }
}
