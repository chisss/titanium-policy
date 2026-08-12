package com.titanium.policy.bootstrap.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

/**
 * Feign错误解码器配置
 * <p>
 * 对于Product等外部服务的404响应，不抛异常而是返回null，
 * 让适配器层的容错逻辑生效。
 * </p>
 */
@Slf4j
@Configuration
public class FeignErrorDecoderConfig {

    @Bean
    public ErrorDecoder errorDecoder() {
        return new ErrorDecoder.Default() {
            @Override
            public Exception decode(String methodKey, Response response) {
                // 对于404/500，包装成特殊标记异常供上层识别并降级
                // （500容错是临时方案：clause服务迁移InsuranceProductType枚举尚未完成）
                if (response.status() == 404 || response.status() == 500) {
                    log.warn("Feign调用返回{}: method={}, url={}, 将触发降级处理",
                            response.status(), methodKey, response.request().url());
                    return new FeignDegradationException(
                            String.format("HTTP %d from %s", response.status(), response.request().url()));
                }
                // 其他错误使用默认处理
                return super.decode(methodKey, response);
            }
        };
    }

    /** 标记可降级的Feign异常（404/500等预期的依赖服务不可用场景） */
    public static class FeignDegradationException extends RuntimeException {
        public FeignDegradationException(String message) {
            super(message);
        }
    }
}
