package com.rocketmq.mdp.enhanced.domain;

import java.io.Serializable;

/**
 * 增强版MDP消息包装类
 * 支持延迟消息和灵活参数转换
 */
public class MdpMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 消息体（JSON格式）
     */
    private String body;

    /**
     * 原始类名（用于灵活转换）
     */
    private String originalClassName;

    /**
     * 消息时间戳
     */
    private Long timestamp;

    /**
     * 延迟级别（0=无延迟，1-18对应不同延迟时间）
     */
    private Integer delayLevel;

    /**
     * Topic
     */
    private String topic;

    /**
     * Tags
     */
    private String tags;

    /**
     * 服务名称
     */
    private String service;

    /**
     * 方法名称
     */
    private String methodName;

    public MdpMessage() {
        this.timestamp = System.currentTimeMillis();
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getOriginalClassName() {
        return originalClassName;
    }

    public void setOriginalClassName(String originalClassName) {
        this.originalClassName = originalClassName;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Integer getDelayLevel() {
        return delayLevel;
    }

    public void setDelayLevel(Integer delayLevel) {
        this.delayLevel = delayLevel;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    @Override
    public String toString() {
        return "EnhancedMdpMessage{" +
                "messageId='" + messageId + '\'' +
                ", originalClassName='" + originalClassName + '\'' +
                ", timestamp=" + timestamp +
                ", delayLevel=" + delayLevel +
                ", topic='" + topic + '\'' +
                ", tags='" + tags + '\'' +
                ", service='" + service + '\'' +
                '}';
    }
}
