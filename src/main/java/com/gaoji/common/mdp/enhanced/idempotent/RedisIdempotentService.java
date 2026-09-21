package com.gaoji.common.mdp.enhanced.idempotent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

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

    public RedisIdempotentService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryProcess(String businessKey, String messageId, long timeout, long lockTimeout) {
        String idempotentKey = IDEMPOTENT_KEY_PREFIX + businessKey;
        String lockKey = LOCK_KEY_PREFIX + businessKey;
        String lockValue = messageId + ":" + System.currentTimeMillis();

        try {
            // 1. 尝试获取分布式锁
            Boolean lockAcquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, lockTimeout, TimeUnit.SECONDS);

            if (!Boolean.TRUE.equals(lockAcquired)) {
                log.warn("获取分布式锁失败，可能存在并发消费 - businessKey: {}, messageId: {}", businessKey, messageId);
                return false;
            }

            try {
                // 2. 检查是否已处理
                String existingMessageId = redisTemplate.opsForValue().get(idempotentKey);
                if (existingMessageId != null) {
                    log.warn("检测到重复消息 - businessKey: {}, 原始messageId: {}, 当前messageId: {}",
                            businessKey, existingMessageId, messageId);
                    return false;
                }

                // 3. 记录处理标识
                redisTemplate.opsForValue().set(idempotentKey, messageId, timeout, TimeUnit.SECONDS);
                log.debug("开始处理消息 - businessKey: {}, messageId: {}, timeout: {}s",
                        businessKey, messageId, timeout);
                return true;

            } finally {
                // 4. 释放分布式锁（使用 Lua 脚本保证原子性）
                releaseLock(lockKey, lockValue);
            }

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
