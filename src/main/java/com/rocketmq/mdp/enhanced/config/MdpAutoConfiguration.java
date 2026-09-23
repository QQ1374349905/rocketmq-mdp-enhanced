package com.rocketmq.mdp.enhanced.config;

import com.rocketmq.mdp.enhanced.register.MdpClientRegister;
import com.rocketmq.mdp.enhanced.register.MdpServerRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 增强版MDP自动配置
 * 当配置了rocketmq.name-server时自动启用
 */
@Configuration
@ConditionalOnProperty(name = "rocketmq.name-server")
public class MdpAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MdpAutoConfiguration.class);

    public MdpAutoConfiguration() {
        log.info("=== 初始化增强版MDP自动配置 ===");
    }

    /**
     * 注册 MDP 消费者注册器
     * 使用 static 方法避免配置类过早实例化
     */
    @Bean
    public static MdpServerRegister mdpServerRegister() {
        log.info("注册增强版MDP消费者注册器");
        return new MdpServerRegister();
    }

    /**
     * 注册 MDP 生产者注册器
     * 使用 static 方法避免配置类过早实例化
     */
    @Bean
    public static MdpClientRegister mdpClientRegister() {
        log.info("注册增强版MDP生产者注册器");
        return new MdpClientRegister();
    }
}
