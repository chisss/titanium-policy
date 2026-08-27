package com.titanium.policy.bootstrap.config;

import org.axonframework.config.Configurer;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.eventhandling.PropagatingErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 出单 Saga 异常处理配置。
 *
 * <p>Axon 4.10 的 Saga manager 不支持事件处理 DLQ。显式传播监听器异常可使当前事务回滚，
 * tracking token 保持不前进，待外部依赖恢复后由处理器重新投递同一事件。</p>
 */
@Configuration(proxyBeanMethods = false)
public class PolicySagaErrorHandlingConfiguration {

    static final String ISSUANCE_SAGA_PROCESSING_GROUP = "IssuanceSagaProcessor";
    static final String PROPOSAL_ISSUANCE_SAGA_PROCESSING_GROUP = "ProposalIssuanceSagaProcessor";

    /**
     * 禁止 Saga 监听器使用默认 LoggingErrorHandler 吞掉技术异常。
     */
    @Bean
    public ConfigurerModule policySagaPropagatingErrorHandlerConfigurerModule() {
        return new ConfigurerModule() {
            @Override
            public void configureModule(Configurer configurer) {
                configurer.eventProcessing()
                        .registerListenerInvocationErrorHandler(
                                ISSUANCE_SAGA_PROCESSING_GROUP,
                                configuration -> PropagatingErrorHandler.instance())
                        .registerListenerInvocationErrorHandler(
                                PROPOSAL_ISSUANCE_SAGA_PROCESSING_GROUP,
                                configuration -> PropagatingErrorHandler.instance());
            }
        };
    }
}
