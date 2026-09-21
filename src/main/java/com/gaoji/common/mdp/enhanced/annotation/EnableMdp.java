package com.gaoji.common.mdp.enhanced.annotation;

import com.gaoji.common.mdp.enhanced.config.IdempotentConfig;
import com.gaoji.common.mdp.enhanced.config.MdpAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 启用增强版MDP功能
 * <p>
 * 用法：在Spring Boot启动类上添加此注解
 * <p>
 * 示例：
 *
 * @SpringBootApplication
 * @EnableMdp(basePackages = "com.gaoji.business")
 * public class Application {
 * public static void main(String[] args) {
 * SpringApplication.run(Application.class, args);
 * }
 * }
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({MdpAutoConfiguration.class, IdempotentConfig.class})
public @interface EnableMdp {

    /**
     * 扫描的基础包路径
     * 默认为空，将扫描启动类所在包及其子包
     */
    String[] basePackages() default {};

    /**
     * 扫描的基础包类型（类型安全的方式指定包）
     */
    Class<?>[] basePackageClasses() default {};
}
