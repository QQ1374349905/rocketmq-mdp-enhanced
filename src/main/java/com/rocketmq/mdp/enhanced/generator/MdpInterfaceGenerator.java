package com.rocketmq.mdp.enhanced.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rocketmq.mdp.enhanced.annotation.MdpClient;
import com.rocketmq.mdp.enhanced.annotation.MdpMethod;
import com.rocketmq.mdp.enhanced.domain.MdpMessage;
import com.rocketmq.mdp.enhanced.enums.DelayLevel;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
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
 * 基于JDK动态代理，为 @MdpClient 接口生成实现类
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
            throw new IllegalArgumentException("接口必须标注 @MdpClient: " + interfaceClass.getName());
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
                throw new IllegalStateException("方法必须标注 @MdpMethod: " + method.getName());
            }

            boolean isSync = methodAnnotation.isSync();
            DelayLevel delayLevelEnum = methodAnnotation.delayLevel();
            String tags = methodAnnotation.tags();

            // 提取参数
            Object messageBody = null;
            if (args != null && args.length > 0) {
                messageBody = args[0];
            }

            // 从注解获取延迟级别
            int delayLevel = delayLevelEnum.getLevel();

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
            if (isSync) {
                SendResult result = producer.send(message, sendTimeout);
                log.debug("同步消息发送成功 - MsgId: {}, Topic: {}", result.getMsgId(), topic);
                return result;
            } else {
                // 异步发送，使用回调，并设置超时时间
                // 注意：即使是异步发送，producer.send() 调用本身也可能立即抛出同步异常
                // 例如：参数校验失败、序列化异常、producer未启动等
                // 这些同步异常会直接抛给调用者，因为消息根本没有发送出去
                try {
                    producer.send(message, new SendCallback() {
                        @Override
                        public void onSuccess(SendResult sendResult) {
                            log.debug("异步消息发送成功 - MsgId: {}, Topic: {}", sendResult.getMsgId(), topic);
                        }

                        @Override
                        public void onException(Throwable e) {
                            log.error("异步消息发送失败 - Topic: {}, MessageId: {}, 错误: {}",
                                    topic, mdpMessage.getMessageId(), e.getMessage(), e);
                        }
                    }, sendTimeout);
                    return null;
                } catch (Exception e) {
                    // 如果希望异步发送完全不抛异常，可以在这里捕获并记录日志
                    log.error("异步消息提交失败（同步异常）- Topic: {}, MessageId: {}, 错误: {}",
                            topic, mdpMessage.getMessageId(), e.getMessage(), e);
                    // 选择1: 抛出异常，让调用者知道消息没发出去（推荐）
                    throw e;
                    // 选择2: 吞掉异常，返回null（不推荐，会导致消息丢失且调用者无感知）
                    // return null;
                }
            }
        }
    }
}
