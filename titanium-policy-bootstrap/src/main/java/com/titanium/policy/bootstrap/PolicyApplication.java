package com.titanium.policy.bootstrap;

import org.axonframework.springboot.autoconfig.AxonServerAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 保单系统启动类
 */
@SpringBootApplication(exclude = { AxonServerAutoConfiguration.class })
// 启用定时任务：年金逐期给付、满期给付等 application/scheduled 驱动，及读侧死信重试
@EnableScheduling
@ComponentScan(basePackages = { "com.titanium.policy", "com.titanium.common" })
// 跨域 Feign：产品/条款/规则引擎/核保/计费 API（各 Adapter 注入其 @FeignClient）
@EnableFeignClients(basePackages = { "com.titanium.product.api", "com.titanium.clause.api",
        "com.titanium.ruleengine.api", "com.titanium.underwriting.api", "com.titanium.billing.api",
        "com.titanium.investment.api" })
// 写侧纯事件溯源，无 JPA 写模型；此处仅扫描 CQRS 读侧读模型（View + ViewRepository）
@EntityScan(basePackages = { "com.titanium.policy.query.view" })
@EnableJpaRepositories(basePackages = { "com.titanium.policy.query.repository" })
public class PolicyApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolicyApplication.class, args);
    }

}
