package com.gaoji.common.mdp.enhanced.register;

import com.gaoji.common.mdp.enhanced.annotation.MdpServer;
import com.gaoji.common.mdp.enhanced.consumer.MdpMessageListener;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PreDestroy;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 增强版MDP消费者注册器
 * 自动扫描 @MdpSEnhanced 注解，注册RocketMQ消费者
 * 支持自动生成topic和group
 */
@Component
public class MdpServerClassRegister implements BeanPostProcessor, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(MdpServerClassRegister.class);

    private ApplicationContext applicationContext;
    private Environment environment;
    private final Map<String, DefaultMQPushConsumer> consumerMap = new ConcurrentHashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        this.environment = applicationContext.getEnvironment();
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> clazz = bean.getClass();
        MdpServer annotation = clazz.getAnnotation(MdpServer.class);

        if (annotation != null) {
            registerConsumer(bean, annotation);
        }

        return bean;
    }

    /**
     * 注册消费者
     */
    private void registerConsumer(Object bean, MdpServer annotation) {
        String service = annotation.service();
        String topic = StringUtils.hasText(annotation.topic()) ? annotation.topic() : service;
        String group = StringUtils.hasText(annotation.group()) ? annotation.group() : service + "_consumer_group";

        // 将组名中的非法字符替换为下划线
        // RocketMQ组名只允许: ^[%|a-zA-Z0-9_-]+$
        group = group.replaceAll("[^a-zA-Z0-9_-]", "_");

        try {
            // 获取RocketMQ配置
            String nameServer = environment.getProperty("rocketmq.name-server");
            if (!StringUtils.hasText(nameServer)) {
                log.warn("RocketMQ name-server 未配置，跳过消费者注册: {}", service);
                return;
            }

            // 创建消费者
            DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(group);
            consumer.setNamesrvAddr(nameServer);
            consumer.subscribe(topic, "*");
            consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
            consumer.setConsumeThreadMax(annotation.maxThreads());
            consumer.setConsumeThreadMin(Math.min(10, annotation.maxThreads()));

            // 设置消息模式
            if ("BROADCASTING".equalsIgnoreCase(annotation.messageModel())) {
                consumer.setMessageModel(MessageModel.BROADCASTING);
            } else {
                consumer.setMessageModel(MessageModel.CLUSTERING);
            }

            // 注册消息监听器（不再预先查找方法，由监听器根据消息中的方法名动态查找）
            MdpMessageListener listener = new MdpMessageListener(
                    bean, null, annotation.flexibleConversion());
            consumer.registerMessageListener(listener);

            // 启动消费者
            consumer.start();
            consumerMap.put(service, consumer);

            log.info("注册增强版MDP消费者成功 - Service: {}, Topic: {}, Group: {}, FlexibleConversion: {}",
                    service, topic, group, annotation.flexibleConversion());
        } catch (MQClientException e) {
            log.error("注册消费者失败 - Service: {}, Error: {}", service, e.getMessage(), e);
            throw new RuntimeException("注册消费者失败: " + service, e);
        }
    }

    /**
     * 关闭所有消费者
     */
    @PreDestroy
    public void shutdown() {
        for (Map.Entry<String, DefaultMQPushConsumer> entry : consumerMap.entrySet()) {
            try {
                entry.getValue().shutdown();
                log.info("关闭消费者: {}", entry.getKey());
            } catch (Exception e) {
                log.error("关闭消费者失败: {}", entry.getKey(), e);
            }
        }
        consumerMap.clear();
    }
}
