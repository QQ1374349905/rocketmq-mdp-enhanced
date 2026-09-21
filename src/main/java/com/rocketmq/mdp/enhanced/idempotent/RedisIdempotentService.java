package com.rocketmq.mdp.enhanced.idempotent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 基于 Redis 的幂等性服务实现
 * <p>
 * 适用场景：
 * - 分布式部署（多实例共享去重状态）
 * - 生产环境
 * - 需要持久化去重记录
 * <p>
 * 特性：
 * - 支持分布式锁（基于 Redis SETNX）
 * - 自动过期（TTL）
 * - 高性能（Redis 内存操作）
 * - 高可用（Redis 集群/哨兵）
 * - 支持自定义TTL配置
 * <p>
 * Redis Key 设计：
 * - 去重记录: mdp:idempotent:{businessKey} -> {messageId}
 * - 分布式锁: mdp:lock:{businessKey} -> {lockValue}
 */
public class RedisIdempotentService implements IdempotentService {

    private static final Logger log = LoggerFactory.getLogger(RedisIdempotentService.class);

    private static final String IDEMPOTENT_KEY_PREFIX = "mdp:idempotent:";
    private static final String LOCK_KEY_PREFIX = "mdp:lock:";

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 默认TTL（秒）- 可通过配置修改
     */
    private long defaultTtl = 86400L; // 24小时

    /**
     * 默认锁超时（秒）- 可通过配置修改
     */
    private long defaultLockTimeout = 10L; // 10秒

    public RedisIdempotentService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void setDefaultTtl(long defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    public void setDefaultLockTimeout(long defaultLockTimeout) {
        this.defaultLockTimeout = defaultLockTimeout;
    }

    @Override
    public boolean tryProcess(String businessKey, String messageId, long timeout, long lockTimeout) {
        String idempotentKey = IDEMPOTENT_KEY_PREFIX + businessKey;
        String lockKey = LOCK_KEY_PREFIX + businessKey;
        String lockValue = messageId + ":" + System.currentTimeMillis();

        // 使用配置的默认值（如果参数未指定）
        long actualTimeout = timeout > 0 ? timeout : defaultTtl;
        long actualLockTimeout = lockTimeout > 0 ? lockTimeout : defaultLockTimeout;

        try {
            // 优化：使用Lua脚本一次性完成检查和设置，减少网络往返
            String luaScript =
                    "local lockKey = KEYS[1] " +
                            "local idempotentKey = KEYS[2] " +
                            "local lockValue = ARGV[1] " +
                            "local messageId = ARGV[2] " +
                            "local lockTimeout = tonumber(ARGV[3]) " +
                            "local idempotentTimeout = tonumber(ARGV[4]) " +
                            "" +
                            "-- 1. 尝试获取分布式锁 " +
                            "local lockAcquired = redis.call('SET', lockKey, lockValue, 'NX', 'EX', lockTimeout) " +
                            "if not lockAcquired then " +
                            "    return {0, 'lock_failed', ''} " +
                            "end " +
                            "" +
                            "-- 2. 检查是否已处理 " +
                            "local existingMessageId = redis.call('GET', idempotentKey) " +
                            "if existingMessageId then " +
                            "    -- 释放锁 " +
                            "    redis.call('DEL', lockKey) " +
                            "    return {0, 'duplicate', existingMessageId} " +
                            "end " +
                            "" +
                            "-- 3. 记录处理标识 " +
                            "redis.call('SET', idempotentKey, messageId, 'EX', idempotentTimeout) " +
                            "" +
                            "-- 4. 释放锁（原子操作） " +
                            "redis.call('DEL', lockKey) " +
                            "" +
                            "return {1, 'success', ''}";

            DefaultRedisScript<List> script = new DefaultRedisScript<>(luaScript, List.class);
            List result = redisTemplate.execute(
                    script,
                    Arrays.asList(lockKey, idempotentKey),
                    lockValue, messageId, String.valueOf(actualLockTimeout), String.valueOf(actualTimeout)
            );

            if (result.size() >= 3) {
                int success = ((Number) result.get(0)).intValue();
                String status = (String) result.get(1);
                String existingMessageId = (String) result.get(2);

                if (success == 1) {
                    log.debug("开始处理消息 - businessKey: {}, messageId: {}, timeout: {}s",
                            businessKey, messageId, actualTimeout);
                    return true;
                } else if ("duplicate".equals(status)) {
                    log.warn("检测到重复消息 - businessKey: {}, 原始messageId: {}, 当前messageId: {}",
                            businessKey, existingMessageId, messageId);
                    return false;
                } else {
                    log.warn("获取分布式锁失败，可能存在并发消费 - businessKey: {}, messageId: {}", businessKey, messageId);
                    return false;
                }
            }

            return false;

        } catch (Exception e) {
            log.error("幂等性检查失败 - businessKey: {}, messageId: {}", businessKey, messageId, e);
            return false;
        }
    }

    @Override
    public void markSuccess(String businessKey, String messageId) {
        String idempotentKey = IDEMPOTENT_KEY_PREFIX + businessKey;

        try {
            // 验证记录是否存在
            String existingMessageId = redisTemplate.opsForValue().get(idempotentKey);
            if (messageId.equals(existingMessageId)) {
                log.debug("标记消息处理成功 - businessKey: {}, messageId: {}", businessKey, messageId);
                // 保持原有的过期时间，不需要额外操作
            } else {
                log.warn("标记成功时发现messageId不匹配 - businessKey: {}, expected: {}, actual: {}",
                        businessKey, existingMessageId, messageId);
            }
        } catch (Exception e) {
            log.error("标记消息成功失败 - businessKey: {}, messageId: {}", businessKey, messageId, e);
        }
    }

    @Override
    public void markFailed(String businessKey, String messageId) {
        String idempotentKey = IDEMPOTENT_KEY_PREFIX + businessKey;

        try {
            // 删除去重记录，允许重试
            Boolean deleted = redisTemplate.delete(idempotentKey);
            log.debug("标记消息处理失败，移除去重记录 - businessKey: {}, messageId: {}, deleted: {}",
                    businessKey, messageId, deleted);
        } catch (Exception e) {
            log.error("标记消息失败失败 - businessKey: {}, messageId: {}", businessKey, messageId, e);
        }
    }

    @Override
    public boolean isProcessed(String businessKey) {
        String idempotentKey = IDEMPOTENT_KEY_PREFIX + businessKey;

        try {
            return redisTemplate.hasKey(idempotentKey);
        } catch (Exception e) {
            log.error("检查消息是否已处理失败 - businessKey: {}", businessKey, e);
            return false;
        }
    }

    @Override
    public void cleanup() {
        // Redis 自动过期（TTL），不需要手动清理
        log.debug("Redis 使用 TTL 自动过期，无需手动清理");
    }

    /**
     * 释放分布式锁
     * 使用 Lua 脚本保证原子性：只有持有锁的线程才能释放
     */
    private void releaseLock(String lockKey, String lockValue) {
        String luaScript =
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "    return redis.call('del', KEYS[1]) " +
                        "else " +
                        "    return 0 " +
                        "end";

        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(luaScript, Long.class);
            Long result = redisTemplate.execute(script, Collections.singletonList(lockKey), lockValue);

            if (result == 1) {
                log.debug("释放分布式锁成功 - lockKey: {}", lockKey);
            } else {
                log.warn("释放分布式锁失败或锁已过期 - lockKey: {}", lockKey);
            }
        } catch (Exception e) {
            log.error("释放分布式锁失败 - lockKey: {}", lockKey, e);
        }
    }

    /**
     * 获取当前去重记录数量（用于监控）
     * 注意：扫描 Redis keys 性能较差，生产环境慎用
     */
    public long getRecordCount() {
        try {
            Set<String> keys = redisTemplate.keys(IDEMPOTENT_KEY_PREFIX + "*");
            return keys.size();
        } catch (Exception e) {
            log.error("获取去重记录数量失败", e);
            return -1;
        }
    }

    /**
     * 清空所有去重记录（用于测试）
     * 警告：生产环境禁止使用
     */
    public void clearAll() {
        try {
            // 清空幂等性记录
            Set<String> idempotentKeys = redisTemplate.keys(IDEMPOTENT_KEY_PREFIX + "*");
            if (!idempotentKeys.isEmpty()) {
                redisTemplate.delete(idempotentKeys);
            }

            // 清空分布式锁
            Set<String> lockKeys = redisTemplate.keys(LOCK_KEY_PREFIX + "*");
            if (!lockKeys.isEmpty()) {
                redisTemplate.delete(lockKeys);
            }

            log.info("已清空所有去重记录（包括锁）");
        } catch (Exception e) {
            log.error("清空去重记录失败", e);
        }
    }
}
