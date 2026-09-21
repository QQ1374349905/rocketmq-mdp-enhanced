package com.gaoji.common.mdp.enhanced.idempotent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 基于内存的幂等性服务实现
 *
 * 适用场景：
 * - 单机部署
 * - 开发测试环境
 * - 对幂等性要求不严格的场景
 *
 * 限制：
 * - 不支持分布式部署（多实例无法共享状态）
 * - 重启后丢失去重记录
 * - 内存占用随消息量增长
 *
 * 生产环境建议使用 Redis 或数据库实现
 */
public class MemoryIdempotentService implements IdempotentService {

    private static final Logger log = LoggerFactory.getLogger(MemoryIdempotentService.class);

    private final Map<String, ProcessRecord> processRecords = new ConcurrentHashMap<>();
    private final Map<String, Lock> locks = new ConcurrentHashMap<>();

    @Override
    public boolean tryProcess(String businessKey, String messageId, long timeout, long lockTimeout) {
        Lock lock = locks.computeIfAbsent(businessKey, k -> new ReentrantLock());

        try {
            if (!lock.tryLock()) {
                log.warn("获取锁失败，可能存在并发消费 - businessKey: {}, messageId: {}", businessKey, messageId);
                return false;
            }

            try {
                ProcessRecord record = processRecords.get(businessKey);

                if (record != null) {
                    if (record.isExpired()) {
                        log.debug("去重记录已过期，允许重新处理 - businessKey: {}", businessKey);
                        processRecords.remove(businessKey);
                    } else {
                        log.warn("检测到重复消息 - businessKey: {}, 原始messageId: {}, 当前messageId: {}",
                                businessKey, record.messageId, messageId);
                        return false;
                    }
                }

                ProcessRecord newRecord = new ProcessRecord(messageId, System.currentTimeMillis(), timeout * 1000);
                processRecords.put(businessKey, newRecord);
                log.debug("开始处理消息 - businessKey: {}, messageId: {}", businessKey, messageId);
                return true;

            } finally {
                lock.unlock();
            }
        } catch (Exception e) {
            log.error("幂等性检查失败 - businessKey: {}, messageId: {}", businessKey, messageId, e);
            return false;
        }
    }

    @Override
    public void markSuccess(String businessKey, String messageId) {
        ProcessRecord record = processRecords.get(businessKey);
        if (record != null) {
            record.status = ProcessStatus.SUCCESS;
            log.debug("标记消息处理成功 - businessKey: {}, messageId: {}", businessKey, messageId);
        }
    }

    @Override
    public void markFailed(String businessKey, String messageId) {
        processRecords.remove(businessKey);
        log.debug("标记消息处理失败，移除去重记录 - businessKey: {}, messageId: {}", businessKey, messageId);
    }

    @Override
    public boolean isProcessed(String businessKey) {
        ProcessRecord record = processRecords.get(businessKey);
        return record != null && !record.isExpired();
    }

    @Override
    public void cleanup() {
        long now = System.currentTimeMillis();
        int beforeSize = processRecords.size();

        processRecords.entrySet().removeIf(entry -> entry.getValue().isExpired());

        int afterSize = processRecords.size();
        if (beforeSize != afterSize) {
            log.info("清理过期去重记录 - 清理前: {}, 清理后: {}, 清理数量: {}",
                    beforeSize, afterSize, beforeSize - afterSize);
        }
    }

    /**
     * 获取当前去重记录数量（用于监控）
     */
    public int getRecordCount() {
        return processRecords.size();
    }

    /**
     * 清空所有去重记录（用于测试）
     */
    public void clearAll() {
        processRecords.clear();
        locks.clear();
        log.info("已清空所有去重记录");
    }

    private static class ProcessRecord {
        String messageId;
        long processTime;
        long expireTime;
        ProcessStatus status;

        ProcessRecord(String messageId, long processTime, long ttl) {
            this.messageId = messageId;
            this.processTime = processTime;
            this.expireTime = processTime + ttl;
            this.status = ProcessStatus.PROCESSING;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }

    private enum ProcessStatus {
        PROCESSING,
        SUCCESS,
        FAILED
    }
}
