package com.titanium.policy.bootstrap;

import org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 保单系统启动类
 */
@SpringBootApplication(exclude = { AxonServerAutoConfiguration.class })
@ComponentScan(basePackages = { "com.titanium.policy", "com.titanium.common" })
@EntityScan(basePackages = { "com.titanium.policy.infrastructure.entity" })
@EnableJpaRepositories(basePackages = { "com.titanium.policy.infrastructure.*" })
public class PolicyApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolicyApplication.class, args);
    }

}
