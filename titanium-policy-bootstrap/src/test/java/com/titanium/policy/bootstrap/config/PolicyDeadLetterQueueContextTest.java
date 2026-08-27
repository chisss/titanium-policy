package com.titanium.policy.bootstrap.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.PropagatingErrorHandler;
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
import com.titanium.product.api.ProductApi;
import com.titanium.product.api.ProductPremiumCalculationApi;
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
        assertEquals(Set.of("policy-query-group"),
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
}
