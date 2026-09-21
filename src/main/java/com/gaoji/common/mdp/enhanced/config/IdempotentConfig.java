package com.gaoji.common.mdp.enhanced.config;

import com.gaoji.common.mdp.enhanced.idempotent.IdempotentProcessor;
import com.gaoji.common.mdp.enhanced.idempotent.IdempotentService;
import com.gaoji.common.mdp.enhanced.idempotent.MemoryIdempotentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 幂等性配置类
 *
 * 提供幂等性服务的默认实现和配置
 *
 * 使用说明：
 * 1. 默认使用内存实现（MemoryIdempotentService）
 * 2. 生产环境建议自定义 IdempotentService Bean，使用 Redis 或数据库实现
 * 3. 每小时自动清理过期的去重记录
 *
 * 自定义实现示例：
 * <pre>
 * {@code @Bean}
 * public IdempotentService idempotentService(RedisTemplate redisTemplate) {
 *     return new RedisIdempotentService(redisTemplate);
 * }
 * </pre>
 */
@Configuration
@EnableScheduling
public class IdempotentConfig {

    private static final Logger log = LoggerFactory.getLogger(IdempotentConfig.class);

    /**
     * 幂等性服务 Bean
     * 默认使用内存实现，可以通过自定义 Bean 替换
     */
    @Bean
    @ConditionalOnMissingBean(IdempotentService.class)
    public IdempotentService idempotentService() {
        log.info("初始化幂等性服务 - 使用内存实现（MemoryIdempotentService）");
        log.warn("内存实现不支持分布式部署，生产环境建议使用 Redis 或数据库实现");
        return new MemoryIdempotentService();
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
     * 定时清理过期的去重记录
     * 每小时执行一次
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void cleanupExpiredRecords() {
        try {
            IdempotentService service = idempotentService();
            if (service != null) {
                log.debug("开始清理过期去重记录");
                service.cleanup();
            }
        } catch (Exception e) {
            log.error("清理过期去重记录失败", e);
        }
    }
}
