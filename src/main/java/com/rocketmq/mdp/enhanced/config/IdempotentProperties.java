package com.rocketmq.mdp.enhanced.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 幂等性配置属性
 *
 * 配置示例（application.yml）：
 * <pre>
 * mdp:
 *   idempotent:
 *     # 幂等性记录过期时间（默认24小时）
 *     ttl: 86400
 *     # 分布式锁超时时间（默认10秒）
 *     lock-timeout: 10
 * </pre>
 */
@ConfigurationProperties(prefix = "mdp.idempotent")
public class IdempotentProperties {

    /**
     * 幂等性记录过期时间（秒）
     * 默认：24小时（86400秒）
     *
     * 说明：
     * - 过期后记录自动删除，节省Redis空间
     * - 建议根据业务场景调整：
     *   - 订单类：24-72小时
     *   - 支付类：7-30天
     *   - 日志类：1-7天
     */
    private long ttl = 86400L;

    /**
     * 分布式锁超时时间（秒）
     * 默认：10秒
     *
     * 说明：
     * - 防止死锁，锁会自动过期
     * - 建议根据消费耗时调整：
     *   - 快速消费：5-10秒
     *   - 慢速消费：30-60秒
     */
    private long lockTimeout = 10L;

    /**
     * 是否启用幂等性（默认启用）
     */
    private boolean enabled = true;

    public long getTtl() {
        return ttl;
    }

    public void setTtl(long ttl) {
        this.ttl = ttl;
    }

    public long getLockTimeout() {
        return lockTimeout;
    }

    public void setLockTimeout(long lockTimeout) {
        this.lockTimeout = lockTimeout;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
