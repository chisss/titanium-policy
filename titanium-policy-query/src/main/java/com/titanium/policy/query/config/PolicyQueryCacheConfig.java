package com.titanium.policy.query.config;

import java.time.Duration;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 保单查询缓存配置
 * <p>
 * 配置查询模块的缓存策略
 * </p>
 */
@Configuration
@EnableCaching
public class PolicyQueryCacheConfig {

    /**
     * 配置Redis缓存管理器
     *
     * @param connectionFactory Redis连接工厂
     * @return Redis缓存管理器
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(10))// 缓存过期时间10分钟
                // key使用String序列化
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
    // ✅ m22-03（审查报告 SPR-04）定性：本处**有意独立构造**——Redis 缓存需要**独立的序列化器**（GenericJacksonJsonRedisSerializer 自带类型信息），
    // 与 HTTP 响应体的 JSON 形态不同，不能共用 Spring 全局实例。
    // ✅ m26-06（AXN-05）：由 Jackson 3 的裸构造改为框架 builder() 默认形态——Jackson 3 已内置 java.time，
    // 裸构造与 builder() 实测 serialize 字节 5/5 逐字一致 + 交叉读通（builder() 默认 defaultTyping=false，与原形态同分支）。
                        .fromSerializer(GenericJacksonJsonRedisSerializer.builder().build()));

        return RedisCacheManager.builder(connectionFactory).cacheDefaults(config).build();
    }

    /**
     * 保单详情缓存配置
     *
     * @return Redis缓存配置
     */
    @Bean
    public RedisCacheConfiguration policyDetailCacheConfig() {
        return RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofHours(1)) // 保单详情缓存1小时
                .prefixCacheNameWith("policy:detail:");
    }

    /**
     * 保单列表缓存配置
     *
     * @return Redis缓存配置
     */
    @Bean
    public RedisCacheConfiguration policyListCacheConfig() {
        return RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(30)) // 保单列表缓存30分钟
                .prefixCacheNameWith("policy:list:");
    }
}
