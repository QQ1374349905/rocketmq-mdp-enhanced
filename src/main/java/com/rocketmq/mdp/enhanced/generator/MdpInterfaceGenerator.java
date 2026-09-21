package com.rocketmq.mdp.enhanced.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketmq.mdp.enhanced.annotation.MdpClient;
import com.rocketmq.mdp.enhanced.annotation.MdpMethod;
import com.rocketmq.mdp.enhanced.domain.MdpMessage;
import com.rocketmq.mdp.enhanced.enums.DelayLevel;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 增强版MDP接口代理生成器
 * 基于JDK动态代理，为 @MdpCEnhanced 接口生成实现类
 * 支持延迟消息发送
 */
public class MdpInterfaceGenerator {

    private static final Logger log = LoggerFactory.getLogger(MdpInterfaceGenerator.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 为接口生成代理实现
     *
     * @param interfaceClass 接口类
     * @param producer RocketMQ生产者
     * @return 代理对象
     */
    @SuppressWarnings("unchecked")
    public static <T> T generateProxy(Class<T> interfaceClass, DefaultMQProducer producer) {
        MdpClient annotation = interfaceClass.getAnnotation(MdpClient.class);
        if (annotation == null) {
            throw new IllegalArgumentException("接口必须标注 @MdpCEnhanced: " + interfaceClass.getName());
        }

        String service = annotation.service();
        String topic = StringUtils.hasText(annotation.topic()) ? annotation.topic() : service;

        // 规范化 topic 名称：RocketMQ topic 只允许 ^[%|a-zA-Z0-9_-]+$
        topic = normalizeTopic(topic);

        int sendTimeout = annotation.sendTimeout();

        InvocationHandler handler = new MdpProducerInvocationHandler(producer, service, topic, sendTimeout);

        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                handler
        );
    }

    /**
     * 规范化 topic 名称
     * RocketMQ topic 只允许: ^[%|a-zA-Z0-9_-]+$
     * 将非法字符替换为下划线
     */
    private static String normalizeTopic(String topic) {
        if (!StringUtils.hasText(topic)) {
            return topic;
        }
        return topic.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    /**
     * 代理调用处理器
     */
    static class MdpProducerInvocationHandler implements InvocationHandler {

        private final DefaultMQProducer producer;
        private final String service;
        private final String topic;
        private final int sendTimeout;

        public MdpProducerInvocationHandler(DefaultMQProducer producer, String service, String topic, int sendTimeout) {
            this.producer = producer;
            this.service = service;
            this.topic = topic;
            this.sendTimeout = sendTimeout;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // 处理Object的方法
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }

            MdpMethod methodAnnotation = method.getAnnotation(MdpMethod.class);
            if (methodAnnotation == null) {
                throw new IllegalStateException("方法必须标注 @MdpMethodEnhanced: " + method.getName());
            }

            boolean isSync = methodAnnotation.isSync();
            boolean supportDelay = methodAnnotation.supportDelay();
            String tags = methodAnnotation.tags();

            // 提取参数
            Object messageBody = null;
            int delayLevel = 0;

            if (args != null && args.length > 0) {
                messageBody = args[0];

                // 如果支持延迟，最后一个参数是延迟级别（支持DelayLevel枚举或int）
                if (supportDelay && args.length > 1) {
                    Object lastArg = args[args.length - 1];
                    if (lastArg instanceof DelayLevel) {
                        delayLevel = ((DelayLevel) lastArg).getLevel();
                    } else if (lastArg instanceof Integer) {
                        delayLevel = (Integer) lastArg;
                    }
                }
            }

            // 发送消息（传入方法名）
            return sendMessage(method.getName(), messageBody, tags, delayLevel, isSync);
        }

        /**
         * 发送消息
         */
        private Object sendMessage(String methodName, Object body, String tags, int delayLevel, boolean isSync) throws Exception {
            // 构建增强消息
            MdpMessage mdpMessage = new MdpMessage();
            mdpMessage.setMessageId(UUID.randomUUID().toString());
            mdpMessage.setService(service);
            mdpMessage.setMethodName(methodName);
            mdpMessage.setTopic(topic);
            mdpMessage.setTags(tags);
            mdpMessage.setDelayLevel(delayLevel);

            if (body != null) {
                mdpMessage.setOriginalClassName(body.getClass().getName());
                mdpMessage.setBody(objectMapper.writeValueAsString(body));
            }

            // 序列化消息
            String json = objectMapper.writeValueAsString(mdpMessage);
            byte[] bodyBytes = json.getBytes(StandardCharsets.UTF_8);

            // 构建RocketMQ消息
            Message message = new Message(topic, tags, bodyBytes);

            // 设置延迟级别
            if (delayLevel > 0 && delayLevel <= 18) {
                message.setDelayTimeLevel(delayLevel);
                log.debug("发送延迟消息 - Topic: {}, DelayLevel: {}, MessageId: {}",
                        topic, delayLevel, mdpMessage.getMessageId());
            } else {
                log.debug("发送消息 - Topic: {}, MessageId: {}", topic, mdpMessage.getMessageId());
            }

            // 发送消息
            SendResult result;
            if (isSync) {
                result = producer.send(message, sendTimeout);
                log.debug("消息发送成功 - MsgId: {}, Topic: {}", result.getMsgId(), topic);
                return result;
            } else {
                producer.send(message, sendTimeout);
                return null;
            }
        }
    }
}
