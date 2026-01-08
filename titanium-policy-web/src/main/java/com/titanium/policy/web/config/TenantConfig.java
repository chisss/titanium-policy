package com.titanium.policy.web.config;

import com.titanium.common.multitenant.TenantWebMvcConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 租户配置类，集成titanium-common中的租户拦截器功能
 */
@Configuration
public class TenantConfig extends TenantWebMvcConfig implements WebMvcConfigurer {
    
    // 这里可以添加项目特定的租户配置
}
