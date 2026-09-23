package com.rocketmq.mdp.enhanced.register;

import com.rocketmq.mdp.enhanced.annotation.EnableMdp;
import com.rocketmq.mdp.enhanced.annotation.MdpClient;
import com.rocketmq.mdp.enhanced.generator.MdpInterfaceGenerator;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PreDestroy;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 增强版MDP生产者注册器
 * 自动扫描 @MdpClient 接口，生成代理实现并注册为Spring Bean
 * 支持自动生成topic
 */
@Component
public class MdpClientRegister implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware {

    private static final Logger log = LoggerFactory.getLogger(MdpClientRegister.class);

    private ApplicationContext applicationContext;
    private final Map<String, DefaultMQProducer> producerMap = new ConcurrentHashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        // BeanDefinition阶段不做处理
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        try {
            Environment environment = beanFactory.getBean(Environment.class);
            String nameServer = environment.getProperty("rocketmq.name-server");

            if (!StringUtils.hasText(nameServer)) {
                log.warn("RocketMQ name-server 未配置，跳过生产者注册");
                return;
            }

            // 扫描并注册生产者接口
            scanAndRegisterProducers(beanFactory, nameServer);
        } catch (Exception e) {
            log.error("注册生产者失败", e);
            throw new RuntimeException("注册生产者失败", e);
        }
    }

    /**
     * 扫描并注册生产者接口
     */
    private void scanAndRegisterProducers(ConfigurableListableBeanFactory beanFactory, String nameServer) throws Exception {
        // 获取扫描路径
        String[] basePackages = getBasePackages();

        // 如果没有配置@EnableMdp注解，不启动
        if (basePackages == null || basePackages.length == 0) {
            log.info("未找到@EnableMdp注解配置，MDP客户端不启动");
            return;
        }

        ResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();
        MetadataReaderFactory metadataReaderFactory = new CachingMetadataReaderFactory(resourcePatternResolver);

        for (String basePackage : basePackages) {
            String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
                    ClassUtils.convertClassNameToResourcePath(basePackage) + "/**/*.class";

            Resource[] resources = resourcePatternResolver.getResources(pattern);

            for (Resource resource : resources) {
                if (resource.isReadable()) {
                    try {
                        MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(resource);

                        // 先通过元数据过滤，避免加载无关的类
                        if (!metadataReader.getClassMetadata().isInterface()) {
                            continue;
                        }

                        // 关键：先检查注解是否存在，避免加载所有接口类
                        if (!metadataReader.getAnnotationMetadata()
                                .hasAnnotation(MdpClient.class.getName())) {
                            continue;
                        }

                        // 只有确认有@MdpClient注解的接口才加载类
                        String className = metadataReader.getClassMetadata().getClassName();
                        Class<?> clazz = Class.forName(className);
                        MdpClient annotation = clazz.getAnnotation(MdpClient.class);

                        if (annotation != null) {
                            registerProducer(beanFactory, clazz, annotation, nameServer);
                        }
                    } catch (Exception e) {
                        log.debug("跳过资源: {}", resource, e);
                    }
                }
            }
        }
    }

    /**
     * 注册生产者
     */
    private void registerProducer(ConfigurableListableBeanFactory beanFactory, Class<?> interfaceClass,
                                  MdpClient annotation, String nameServer) {
        String service = annotation.service();
        String topic = StringUtils.hasText(annotation.topic()) ? annotation.topic() : service;

        try {
            // 创建生产者组名，将service中的非法字符替换为下划线
            // RocketMQ组名只允许: ^[%|a-zA-Z0-9_-]+$
            String groupName = "producer_" + service.replaceAll("[^a-zA-Z0-9_-]", "_");

            // 创建生产者
            DefaultMQProducer producer = new DefaultMQProducer(groupName);
            producer.setNamesrvAddr(nameServer);
            producer.setSendMsgTimeout(annotation.sendTimeout());
            producer.start();

            producerMap.put(service, producer);

            // 生成代理对象
            Object proxyBean = MdpInterfaceGenerator.generateProxy(interfaceClass, producer);

            // 注册为Spring Bean
            beanFactory.registerSingleton(interfaceClass.getName(), proxyBean);

            log.info("注册增强版MDP生产者成功 - Service: {}, Topic: {}, Group: {}, Interface: {}",
                    service, topic, groupName, interfaceClass.getSimpleName());
        } catch (MQClientException e) {
            log.error("注册生产者失败 - Service: {}, Error: {}", service, e.getMessage(), e);
            throw new RuntimeException("注册生产者失败: " + service, e);
        }
    }

    /**
     * 获取扫描基础包路径
     */
    private String[] getBasePackages() {
        Set<String> basePackages = new LinkedHashSet<>();

        // 查找标注了@EnableMdp的类
        Map<String, Object> beansWithAnnotation = applicationContext.getBeansWithAnnotation(EnableMdp.class);

        for (Object bean : beansWithAnnotation.values()) {
            Class<?> beanClass = bean.getClass();
            EnableMdp enableMdp = AnnotationUtils.findAnnotation(beanClass, EnableMdp.class);

            if (enableMdp != null) {
                // 获取basePackages配置
                String[] configuredPackages = enableMdp.basePackages();
                for (String pkg : configuredPackages) {
                    if (StringUtils.hasText(pkg)) {
                        basePackages.add(pkg);
                    }
                }

                // 获取basePackageClasses配置
                Class<?>[] basePackageClasses = enableMdp.basePackageClasses();
                for (Class<?> clazz : basePackageClasses) {
                    basePackages.add(clazz.getPackage().getName());
                }

                // 如果都没配置，使用启动类所在包
                if (basePackages.isEmpty()) {
                    basePackages.add(ClassUtils.getPackageName(beanClass));
                }
            }
        }

        // 如果没有@EnableMdp注解，返回空数组
        if (basePackages.isEmpty()) {
            log.info("未找到@EnableMdp注解配置，MDP客户端不启动");
            return new String[0];
        }

        log.info("MDP客户端扫描包路径: {}", basePackages);
        return basePackages.toArray(new String[0]);
    }

    /**
     * 关闭所有生产者
     */
    @PreDestroy
    public void shutdown() {
        for (Map.Entry<String, DefaultMQProducer> entry : producerMap.entrySet()) {
            try {
                entry.getValue().shutdown();
                log.info("关闭生产者: {}", entry.getKey());
            } catch (Exception e) {
                log.error("关闭生产者失败: {}", entry.getKey(), e);
            }
        }
        producerMap.clear();
    }
}
