package com.rocketmq.mdp.enhanced.enums;

/**
 * RocketMQ延迟消息级别枚举
 *
 * RocketMQ不支持任意时间的延迟，只支持预设的18个延迟级别
 */
public enum DelayLevel {

    /** 不延迟 */
    NONE(0, "不延迟"),

    /** 1秒 */
    SECONDS_1(1, "1s"),

    /** 5秒 */
    SECONDS_5(2, "5s"),

    /** 10秒 */
    SECONDS_10(3, "10s"),

    /** 30秒 */
    SECONDS_30(4, "30s"),

    /** 1分钟 */
    MINUTES_1(5, "1m"),

    /** 2分钟 */
    MINUTES_2(6, "2m"),

    /** 3分钟 */
    MINUTES_3(7, "3m"),

    /** 4分钟 */
    MINUTES_4(8, "4m"),

    /** 5分钟 */
    MINUTES_5(9, "5m"),

    /** 6分钟 */
    MINUTES_6(10, "6m"),

    /** 7分钟 */
    MINUTES_7(11, "7m"),

    /** 8分钟 */
    MINUTES_8(12, "8m"),

    /** 9分钟 */
    MINUTES_9(13, "9m"),

    /** 10分钟 */
    MINUTES_10(14, "10m"),

    /** 20分钟 */
    MINUTES_20(15, "20m"),

    /** 30分钟 */
    MINUTES_30(16, "30m"),

    /** 1小时 */
    HOURS_1(17, "1h"),

    /** 2小时 */
    HOURS_2(18, "2h");

    private final int level;
    private final String description;

    DelayLevel(int level, String description) {
        this.level = level;
        this.description = description;
    }

    /**
     * 获取延迟级别（用于RocketMQ API）
     */
    public int getLevel() {
        return level;
    }

    /**
     * 获取延迟时长描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 根据level获取枚举
     */
    public static DelayLevel fromLevel(int level) {
        for (DelayLevel delayLevel : values()) {
            if (delayLevel.level == level) {
                return delayLevel;
            }
        }
        throw new IllegalArgumentException("不支持的延迟级别: " + level);
    }

    @Override
    public String toString() {
        return "DelayLevel." + name() + " (" + description + ")";
    }
}
