package com.titanium.policy.bootstrap;

import org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 保单系统启动类
 */
@SpringBootApplication(exclude = { AxonServerAutoConfiguration.class })
@ComponentScan(basePackages = { "com.titanium.policy", "com.titanium.common" })
// 跨域 Feign：产品/条款/规则引擎/核保/计费 API（各 Adapter 注入其 @FeignClient）
@EnableFeignClients(basePackages = { "com.titanium.product.api", "com.titanium.clause.api",
        "com.titanium.ruleengine.api", "com.titanium.underwriting.api", "com.titanium.billing.api",
        "com.titanium.investment.api" })
@EntityScan(basePackages = { "com.titanium.policy.infrastructure.entity" })
@EnableJpaRepositories(basePackages = { "com.titanium.policy.infrastructure.repository" })
public class PolicyApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolicyApplication.class, args);
    }

}
