package com.titanium.policy.bootstrap.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.PropagatingErrorHandler;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.axonframework.eventhandling.deadletter.jpa.DeadLetterEntry;
import org.axonframework.springboot.EventProcessorProperties;
import org.axonframework.springboot.autoconfig.JpaAutoConfiguration;
import org.axonframework.springboot.util.DeadLetterQueueProviderConfigurerModule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport.ConditionAndOutcomes;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.titanium.billing.api.BillApi;
import com.titanium.billing.api.BillingAccountApi;
import com.titanium.billing.api.PremiumCalculationApi;
import com.titanium.clause.api.ClauseApi;
import com.titanium.customer.api.CustomerApi;
import com.titanium.investment.api.InvestmentAccountApi;
import com.titanium.payment.api.PaymentApi;
import com.titanium.policy.application.saga.IssuanceSaga;
import com.titanium.policy.application.saga.ProposalIssuanceSaga;
import com.titanium.policy.bootstrap.PolicyApplication;
import com.titanium.policy.infrastructure.event.KafkaEventPublisher;
import com.titanium.product.api.ProductApi;
import com.titanium.product.api.ProductPremiumCalculationApi;
import com.titanium.product.api.ProductSurrenderValueApi;
import com.titanium.product.api.ProductTemplateApi;
import com.titanium.ruleengine.api.RuleEngineApi;
import com.titanium.underwriting.api.UnderwritingApi;

import jakarta.persistence.EntityManagerFactory;

@SpringBootTest(classes = PolicyApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:policy-dlq;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.liquibase.enabled=false",
                "spring.task.scheduling.enabled=false",
                "axon.axonserver.enabled=false"
        })
@MockitoBean(types = {
        BillApi.class,
        BillingAccountApi.class,
        PremiumCalculationApi.class,
        ClauseApi.class,
        CustomerApi.class,
        InvestmentAccountApi.class,
        PaymentApi.class,
        ProductApi.class,
        ProductPremiumCalculationApi.class,
        ProductSurrenderValueApi.class,
        ProductTemplateApi.class,
        RuleEngineApi.class,
        UnderwritingApi.class
})
class PolicyDeadLetterQueueContextTest {

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private EventProcessingConfiguration eventProcessingConfiguration;

    @Autowired
    private EventProcessorProperties eventProcessorProperties;

    @Test
    void providesPersistentDeadLetterProcessorWhenJpaAutoConfigurationDoesNotMatch() {
        Map<String, DeadLetterQueueProviderConfigurerModule> providers =
                applicationContext.getBeansOfType(DeadLetterQueueProviderConfigurerModule.class);
        ConditionEvaluationReport report = ConditionEvaluationReport.get(applicationContext.getBeanFactory());
        ConditionAndOutcomes outcomes = report.getConditionAndOutcomesBySource()
                .get(JpaAutoConfiguration.class.getName());

        assertEquals(1, providers.size());
        assertEquals(Set.of("policyDeadLetterQueueProviderConfigurerModule"), providers.keySet());
        assertNotNull(outcomes);
        assertFalse(outcomes.isFullMatch());
        assertTrue(entityManagerFactory.getMetamodel().getEntities().stream()
                .anyMatch(entityType -> entityType.getJavaType().equals(DeadLetterEntry.class)));
        // 出站组（policy-kafka-group）随 m6-909 加入：读侧投影组与跨域外发组各自独立启用 DLQ，故为两组
        assertEquals(Set.of("policy-query-group", "policy-kafka-group"),
                eventProcessorProperties.getProcessors().keySet());
        assertTrue(eventProcessingConfiguration.sequencedDeadLetterProcessor("policy-query-group").isPresent());
        assertEquals(PolicySagaErrorHandlingConfiguration.ISSUANCE_SAGA_PROCESSING_GROUP,
                eventProcessingConfiguration.sagaProcessingGroup(IssuanceSaga.class));
        assertEquals(PolicySagaErrorHandlingConfiguration.PROPOSAL_ISSUANCE_SAGA_PROCESSING_GROUP,
                eventProcessingConfiguration.sagaProcessingGroup(ProposalIssuanceSaga.class));
        assertFalse(eventProcessingConfiguration.sequencedDeadLetterProcessor(
                PolicySagaErrorHandlingConfiguration.ISSUANCE_SAGA_PROCESSING_GROUP).isPresent());
        assertFalse(eventProcessingConfiguration.sequencedDeadLetterProcessor(
                PolicySagaErrorHandlingConfiguration.PROPOSAL_ISSUANCE_SAGA_PROCESSING_GROUP).isPresent());
        assertEquals(PropagatingErrorHandler.instance(), eventProcessingConfiguration.listenerInvocationErrorHandler(
                PolicySagaErrorHandlingConfiguration.ISSUANCE_SAGA_PROCESSING_GROUP));
        assertEquals(PropagatingErrorHandler.instance(), eventProcessingConfiguration.listenerInvocationErrorHandler(
                PolicySagaErrorHandlingConfiguration.PROPOSAL_ISSUANCE_SAGA_PROCESSING_GROUP));
    }

    /**
     * 跨域外发处理组须以 <b>tracking</b> 模式运行并启用死信队列（m6-909 改造）。
     * <p>
     * 三条断言各有分工：① 组已注册——{@code @ProcessingGroup} 的值与 application.yml 中
     * {@code axon.eventhandling.processors.<name>} 的键必须一致，漂移时处理器仍存在（不报错）却退回默认配置；
     * ② 是 tracking——死信队列只支持流式处理器，subscribing 拿不到 DLQ；③ DLQ 可获取——
     * {@code dlq.enabled: true} 真正生效，这是 {@code DeadLetterQueueService} 重投的前提。
     * </p>
     * <p>
     * ⚠️ tracking 的已知代价是「首启位点」：令牌不存在时会从事件流<b>头部</b>开始消费。该风险已由
     * application.yml 的 {@code titanium.axon.outbound-relay.groups} 登记消解（登记本组后首启位点取流末端）。
     * 故改动本组配置时须同步检查该登记项，二者是一组。
     * </p>
     */
    @Test
    void providesTrackingKafkaPublisherProcessingGroupWithDeadLetterQueue() {
        var processor = eventProcessingConfiguration
                .eventProcessorByProcessingGroup(KafkaEventPublisher.PROCESSING_GROUP);
        assertTrue(processor.isPresent(),
                "未注册处理组 " + KafkaEventPublisher.PROCESSING_GROUP
                        + "：@ProcessingGroup 与 application.yml 的 processors 键不一致");
        assertInstanceOf(TrackingEventProcessor.class, processor.get(),
                "处理组 " + KafkaEventPublisher.PROCESSING_GROUP
                        + " 未按 tracking 模式装配：subscribing 拿不到死信队列，发布失败即永久丢失");
        assertTrue(eventProcessingConfiguration
                .sequencedDeadLetterProcessor(KafkaEventPublisher.PROCESSING_GROUP).isPresent(),
                "处理组 " + KafkaEventPublisher.PROCESSING_GROUP
                        + " 未启用死信队列：需在 application.yml 该组下配 dlq.enabled=true，"
                        + "否则 DeadLetterQueueService 静默空转、失败事件永不重投");
    }
}
