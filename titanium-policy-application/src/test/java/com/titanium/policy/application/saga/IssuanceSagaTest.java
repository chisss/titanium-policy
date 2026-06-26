package com.titanium.policy.application.saga;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.test.matchers.Matchers;
import org.axonframework.test.saga.SagaTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.command.ReceiveUnderwritingResultCommand;
import com.titanium.policy.command.TriggerIssuanceCommand;
import com.titanium.policy.event.insurance.InsuranceCreatedEvent;
import com.titanium.policy.event.insurance.InsuranceIssuedEvent;
import com.titanium.policy.event.insurance.InsuranceSubmittedForUnderwritingEvent;
import com.titanium.policy.event.insurance.UnderwritingResultReceivedEvent;
import com.titanium.policy.service.PolicyNoGenerator;
import com.titanium.policy.service.UnderwritingDecisionGateway;
import com.titanium.policy.valueobject.insurance.UnderwritingDecisionRequest;
import com.titanium.policy.valueobject.insurance.UnderwritingResult;

/**
 * 投保出单 Saga 全链路编排测试
 * <p>
 * 验证投保 → 核保 → 承保 → 出单 各步骤的事件驱动流转：
 * <ol>
 *     <li>投保单创建启动 Saga 并建立关联；</li>
 *     <li>提交核保经核保网关获取结论并回写结果命令；</li>
 *     <li>核保通过触发承保出单命令；核保拒绝结束 Saga；</li>
 *     <li>承保出单创建正式保单并结束 Saga。</li>
 * </ol>
 * 核保网关以可控桩对象替身，保证测试确定性、不依赖核保域真实服务。
 * </p>
 */
public class IssuanceSagaTest {

    private static final String INSURANCE_ID = "INS_001";
    private static final String TENANT_ID    = "TENANT_001";

    private SagaTestFixture<IssuanceSaga> fixture;
    private StubUnderwritingDecisionGateway underwritingGateway;

    @BeforeEach
    public void setUp() {
        fixture = new SagaTestFixture<>(IssuanceSaga.class);
        underwritingGateway = new StubUnderwritingDecisionGateway();
        // 注册 Saga 所需的注入资源（命令网关由夹具记录所发命令）
        fixture.registerCommandGateway(CommandGateway.class);
        fixture.registerResource(underwritingGateway);
        fixture.registerResource(new PolicyNoGenerator());
    }

    /**
     * 投保单创建 → 启动 Saga 并与 insuranceId 建立关联
     */
    @Test
    public void testInsuranceCreatedStartsSaga() {
        fixture.givenNoPriorActivity()
                .whenPublishingA(insuranceCreatedEvent())
                .expectActiveSagas(1)
                .expectAssociationWith("insuranceId", INSURANCE_ID)
                .expectNoDispatchedCommands();
    }

    /**
     * 提交核保 → 经核保网关获取结论，回写 ReceiveUnderwritingResultCommand
     */
    @Test
    public void testSubmitUnderwritingDispatchesReceiveResult() {
        UnderwritingResult approved = approvedResult();
        underwritingGateway.setResult(approved);

        fixture.givenAggregate(INSURANCE_ID).published(insuranceCreatedEvent())
                .whenAggregate(INSURANCE_ID).publishes(insuranceSubmittedEvent())
                .expectActiveSagas(1)
                .expectDispatchedCommands(
                        new ReceiveUnderwritingResultCommand(INSURANCE_ID, approved, TENANT_ID));
    }

    /**
     * 核保通过 → 触发承保出单命令，Saga 保持活跃
     */
    @Test
    public void testUnderwritingApprovedTriggersIssuance() {
        fixture.givenAggregate(INSURANCE_ID).published(insuranceCreatedEvent())
                .whenAggregate(INSURANCE_ID).publishes(underwritingResultEvent(ConclusionType.ACCEPT))
                .expectActiveSagas(1)
                .expectDispatchedCommands(new TriggerIssuanceCommand(INSURANCE_ID, TENANT_ID));
    }

    /**
     * 核保拒绝 → 不触发承保，结束 Saga
     */
    @Test
    public void testUnderwritingRejectedEndsSaga() {
        fixture.givenAggregate(INSURANCE_ID).published(insuranceCreatedEvent())
                .whenAggregate(INSURANCE_ID).publishes(underwritingResultEvent(ConclusionType.REJECT))
                .expectActiveSagas(0)
                .expectNoDispatchedCommands();
    }

    /**
     * 核保暂缓 → 不触发承保，结束 Saga（待人工介入后另行驱动）
     */
    @Test
    public void testUnderwritingSuspendedEndsSaga() {
        fixture.givenAggregate(INSURANCE_ID).published(insuranceCreatedEvent())
                .whenAggregate(INSURANCE_ID).publishes(underwritingResultEvent(ConclusionType.POSTPONE))
                .expectActiveSagas(0)
                .expectNoDispatchedCommands();
    }

    /**
     * 承保出单 → 创建正式保单并结束 Saga
     * <p>policyId/policyNo 由 Saga 内部生成，故用谓词匹配关键字段而非全等比较。</p>
     */
    @Test
    public void testInsuranceIssuedCreatesPolicyAndEndsSaga() {
        fixture.givenAggregate(INSURANCE_ID).published(insuranceCreatedEvent())
                .whenAggregate(INSURANCE_ID).publishes(insuranceIssuedEvent())
                .expectActiveSagas(0)
                .expectDispatchedCommandsMatching(Matchers.payloadsMatching(Matchers.exactSequenceOf(
                        Matchers.<CreatePolicyCommand>predicate(cmd ->
                                cmd instanceof CreatePolicyCommand c
                                        && INSURANCE_ID.equals(c.insuranceId())
                                        && TENANT_ID.equals(c.tenantId())
                                        && PolicyForm.INDIVIDUAL == c.policyForm()
                                        && "HOLDER_001".equals(c.policyHolderId())
                                        && c.policyId() != null
                                        && c.policyNo() != null),
                        Matchers.andNoMore())));
    }

    // ==================== 测试夹具数据 ====================

    private InsuranceCreatedEvent insuranceCreatedEvent() {
        return new InsuranceCreatedEvent(INSURANCE_ID, "INS_NO_001", null, PolicyForm.INDIVIDUAL, "HOLDER_001", 1,
                new BigDecimal("1000.00"), LocalDateTime.now(), LocalDateTime.now().plusYears(1),
                List.of("PRODUCT_001"), 0, LocalDateTime.now(), TENANT_ID);
    }

    private InsuranceSubmittedForUnderwritingEvent insuranceSubmittedEvent() {
        return new InsuranceSubmittedForUnderwritingEvent(INSURANCE_ID, "INS_NO_001", "HOLDER_001", 1,
                new BigDecimal("1000.00"), "CNY", LocalDateTime.now(), LocalDateTime.now().plusYears(1),
                List.of("PRODUCT_001"), 0, PolicyForm.INDIVIDUAL, TENANT_ID);
    }

    private UnderwritingResultReceivedEvent underwritingResultEvent(ConclusionType resultCode) {
        return new UnderwritingResultReceivedEvent(INSURANCE_ID, "UW_001", resultCode, "核保意见", "UW_USER_001",
                LocalDateTime.now(), null, TENANT_ID);
    }

    private InsuranceIssuedEvent insuranceIssuedEvent() {
        return new InsuranceIssuedEvent(INSURANCE_ID, "INS_NO_001", LocalDateTime.now(), TENANT_ID);
    }

    private UnderwritingResult approvedResult() {
        return new UnderwritingResult("UW_001", ConclusionType.ACCEPT, "核保通过", "UW_USER_001",
                LocalDateTime.now(), null);
    }

    /**
     * 核保决策网关桩：返回可配置的固定结论，保证测试确定性
     */
    private static class StubUnderwritingDecisionGateway implements UnderwritingDecisionGateway {
        private UnderwritingResult result;

        void setResult(UnderwritingResult result) {
            this.result = result;
        }

        @Override
        public UnderwritingResult requestDecision(UnderwritingDecisionRequest request) {
            return result;
        }
    }
}
