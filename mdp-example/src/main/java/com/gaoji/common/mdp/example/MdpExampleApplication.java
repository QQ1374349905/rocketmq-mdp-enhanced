package com.gaoji.common.mdp.example;

import com.rocketmq.mdp.enhanced.annotation.EnableMdp;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MDP示例应用启动类
 */
@EnableMdp
@SpringBootApplication
public class MdpExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(MdpExampleApplication.class, args);
    }
}
