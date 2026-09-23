package com.rocketmq.mdp.enhanced.idempotent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    private final StringRedisTemplate redisTemplate;

    /**
     * 默认TTL（秒）- 可通过配置修改
     */
    private long defaultTtl = 86400L; // 24小时

    /**
     * 默认锁超时（秒）- 可通过配置修改
     */
    private long defaultLockTimeout = 10L; // 10秒

    public RedisIdempotentService(StringRedisTemplate redisTemplate) {
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
            // 测试：使用最简单的Lua脚本验证Redis连接
            String testScript = "return {1, 'test', 'ok'}";
            DefaultRedisScript<List> testScriptObj = new DefaultRedisScript<>(testScript, List.class);
            List testResult = redisTemplate.execute(testScriptObj, Collections.emptyList());
            log.info("测试Lua脚本 - result: {}", testResult);

            // 优化：使用Lua脚本一次性完成检查和设置，减少网络往返
            // 注意：Lua脚本返回的数字类型会被序列化为Long，字符串保持为String
            String luaScript =
                    "local lockKey = KEYS[1]\n" +
                            "local idempotentKey = KEYS[2]\n" +
                            "local lockValue = ARGV[1]\n" +
                            "local messageId = ARGV[2]\n" +
                            "local lockTimeout = tonumber(ARGV[3])\n" +
                            "local idempotentTimeout = tonumber(ARGV[4])\n" +
                            "\n" +
                            "-- 1. 尝试获取分布式锁 (SETNX 返回 1 成功, 0 失败)\n" +
                            "local lockAcquired = redis.call('SETNX', lockKey, lockValue)\n" +
                            "if lockAcquired == 0 then\n" +
                            "    return {'0', 'lock_failed', ''}\n" +
                            "end\n" +
                            "redis.call('EXPIRE', lockKey, lockTimeout)\n" +
                            "\n" +
                            "-- 2. 检查是否已处理\n" +
                            "local existingMessageId = redis.call('GET', idempotentKey)\n" +
                            "if existingMessageId then\n" +
                            "    -- 释放锁\n" +
                            "    redis.call('DEL', lockKey)\n" +
                            "    return {'0', 'duplicate', existingMessageId}\n" +
                            "end\n" +
                            "\n" +
                            "-- 3. 记录处理标识 (SETEX 更原子)\n" +
                            "redis.call('SETEX', idempotentKey, idempotentTimeout, messageId)\n" +
                            "\n" +
                            "-- 4. 释放锁\n" +
                            "redis.call('DEL', lockKey)\n" +
                            "\n" +
                            "return {'1', 'success', ''}";

            // 调试：打印Lua脚本检查点
            log.info("执行Lua脚本 - businessKey: {}, lockTimeout: {}, idempotentTimeout: {}",
                    businessKey, actualLockTimeout, actualTimeout);

            DefaultRedisScript<List> script = new DefaultRedisScript<>(luaScript, List.class);

            // 添加详细的异常捕获
            List result;
            try {
                result = redisTemplate.execute(
                        script,
                        Arrays.asList(lockKey, idempotentKey),
                        lockValue, messageId, String.valueOf(actualLockTimeout), String.valueOf(actualTimeout)
                );
            } catch (Exception redisEx) {
                log.error("Redis执行Lua脚本异常 - businessKey: {}, error: {}", businessKey, redisEx.getMessage(), redisEx);
                throw redisEx;
            }

            // 调试日志：打印Lua脚本返回结果（包含类型信息）
            log.info("Lua脚本返回结果 - businessKey: {}, result: {}, type: {}, size: {}",
                    businessKey, result,
                    result != null ? result.getClass().getName() : "null",
                    result != null ? result.size() : "null");

            // 打印每个元素的类型和值
            if (result != null && !result.isEmpty()) {
                for (int i = 0; i < result.size(); i++) {
                    Object elem = result.get(i);
                    log.info("  - result[{}]: value={}, type={}", i, elem, elem != null ? elem.getClass().getName() : "null");
                }
            }

            if (result != null && result.size() >= 3) {
                // Lua脚本返回的是字符串数组 ['1', 'success', ''] 或 ['0', 'duplicate', existingMessageId]
                String successStr = String.valueOf(result.get(0));
                String status = String.valueOf(result.get(1));
                String existingMessageId = result.get(2) != null ? String.valueOf(result.get(2)) : "";

                int success = "1".equals(successStr) ? 1 : 0;

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
            } else {
                log.error("Lua脚本返回结果异常 - businessKey: {}, messageId: {}, result: {}",
                        businessKey, messageId, result);
                // 降级处理：允许继续处理
                return true;
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
