package com.rocketmq.mdp.enhanced.config;

import com.rocketmq.mdp.enhanced.idempotent.IdempotentProcessor;
import com.rocketmq.mdp.enhanced.idempotent.IdempotentService;
import com.rocketmq.mdp.enhanced.idempotent.MemoryIdempotentService;
import com.rocketmq.mdp.enhanced.idempotent.RedisIdempotentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 幂等性配置类
 *
 * 提供幂等性服务的自动配置
 *
 * 自动选择实现：
 * 1. 如果存在 RedisTemplate Bean → 使用 RedisIdempotentService（推荐）
 * 2. 否则 → 使用 MemoryIdempotentService（仅适合单机/测试）
 *
 * 使用说明：
 * - 生产环境：配置 Redis，自动使用 RedisIdempotentService
 * - 开发/测试：不配置 Redis，自动使用 MemoryIdempotentService
 * - 自定义实现：定义自己的 IdempotentService Bean，自动覆盖默认实现
 *
 * Redis 配置示例（application.yml）：
 * <pre>
 * spring:
 *   redis:
 *     host: localhost
 *     port: 6379
 *     password: your_password
 *     database: 0
 *     timeout: 5000ms
 *     lettuce:
 *       pool:
 *         max-active: 8
 *         max-idle: 8
 *         min-idle: 0
 * </pre>
 */
@Configuration
@EnableScheduling
public class IdempotentConfig {

    private static final Logger log = LoggerFactory.getLogger(IdempotentConfig.class);

    private IdempotentService idempotentService;

    /**
     * Redis 幂等性服务 Bean（优先级高）
     * 当存在 RedisConnectionFactory 时自动配置
     * 直接使用 Spring Boot 自动配置的 StringRedisTemplate
     */
    @Bean
    @ConditionalOnClass(RedisConnectionFactory.class)
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(IdempotentService.class)
    public IdempotentService redisIdempotentService(StringRedisTemplate stringRedisTemplate) {
        log.info("初始化幂等性服务 - 使用 Redis 实现（RedisIdempotentService）");
        log.info("✅ Redis 实现支持分布式部署，适合生产环境");
        IdempotentService service = new RedisIdempotentService(stringRedisTemplate);
        this.idempotentService = service;
        return service;
    }

    /**
     * 内存幂等性服务 Bean（兜底实现）
     * 当不存在 RedisConnectionFactory 时使用
     */
    @Bean
    @ConditionalOnMissingBean({RedisConnectionFactory.class, IdempotentService.class})
    public IdempotentService memoryIdempotentService() {
        log.info("初始化幂等性服务 - 使用内存实现（MemoryIdempotentService）");
        log.warn("⚠️  内存实现不支持分布式部署，仅适合单机/测试环境");
        log.warn("⚠️  生产环境建议配置 Redis 以使用 RedisIdempotentService");
        IdempotentService service = new MemoryIdempotentService();
        this.idempotentService = service;
        return service;
    }

    /**
     * 幂等性处理器 Bean
     */
    @Bean
    @ConditionalOnMissingBean(IdempotentProcessor.class)
    public IdempotentProcessor idempotentProcessor(IdempotentService idempotentService) {
        log.info("初始化幂等性处理器 - IdempotentProcessor");
        return new IdempotentProcessor(idempotentService);
    }

    /**
     * 定时清理过期的去重记录（仅内存实现需要）
     * Redis 实现使用 TTL 自动过期，不需要定时清理
     * 每小时执行一次
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void cleanupExpiredRecords() {
        try {
            if (idempotentService instanceof MemoryIdempotentService) {
                log.debug("开始清理过期去重记录（内存实现）");
                idempotentService.cleanup();
            }
            // Redis 实现不需要清理
        } catch (Exception e) {
            log.error("清理过期去重记录失败", e);
        }
    }
}
