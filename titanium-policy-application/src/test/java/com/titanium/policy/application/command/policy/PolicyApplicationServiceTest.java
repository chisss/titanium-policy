package com.titanium.policy.application.command.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;
import com.titanium.metadata.enums.customer.CustomerEnum.IdCardType;
import com.titanium.policy.application.orchestration.issuance.orchestrator.IssuanceOrchestrator;
import com.titanium.policy.command.AddInsuredMemberCommand;
import com.titanium.policy.command.RemoveInsuredMemberCommand;
import com.titanium.policy.common.enums.FamilyRelation;
import com.titanium.policy.entity.insurance.InsuredPartyList;

/**
 * 被保险人成员增减应用门面测试（G12/g12-06 生产入口）
 * <p>
 * 守两件事：① 增员/减员命令经 CommandGateway 原样派发（门面薄、零业务规则——形态与状态守卫
 * 由聚合根 {@code Policy} 承担）；② 命令载荷不丢字段（成员快照与 familyRelation 原样透传）。
 * </p>
 */
class PolicyApplicationServiceTest {

    private final CommandGateway commandGateway = mock(CommandGateway.class);

    private PolicyApplicationService service;

    @BeforeEach
    void setUp() {
        service = new PolicyApplicationService(commandGateway, mock(IssuanceOrchestrator.class));
    }

    @Test
    void shouldDispatchAddInsuredMemberCommandUnchanged() {
        InsuredPartyList.InsuredInfo member = new InsuredPartyList.InsuredInfo("CUSTOMER_001", "INSURED_001",
                "张三", IdCardType.CHINA_ID_CARD, "110101199001011234", 35, CustomerGender.MALE, "SELF", null);
        AddInsuredMemberCommand command = new AddInsuredMemberCommand(
                "POLICY_001", member, FamilyRelation.CHILD, "operator-1", "TENANT_001");

        service.addInsuredMember(command);

        ArgumentCaptor<AddInsuredMemberCommand> captor = ArgumentCaptor.forClass(AddInsuredMemberCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());
        assertEquals("POLICY_001", captor.getValue().policyId());
        assertEquals(member, captor.getValue().member());
        assertEquals(FamilyRelation.CHILD, captor.getValue().familyRelation());
        assertEquals("operator-1", captor.getValue().operatorId());
        assertEquals("TENANT_001", captor.getValue().tenantId());
    }

    @Test
    void shouldDispatchRemoveInsuredMemberCommandUnchanged() {
        RemoveInsuredMemberCommand command = new RemoveInsuredMemberCommand(
                "POLICY_001", "INSURED_001", "员工离职", "operator-1", "TENANT_001");

        service.removeInsuredMember(command);

        ArgumentCaptor<RemoveInsuredMemberCommand> captor =
                ArgumentCaptor.forClass(RemoveInsuredMemberCommand.class);
        verify(commandGateway).sendAndWait(captor.capture());
        assertEquals("POLICY_001", captor.getValue().policyId());
        assertEquals("INSURED_001", captor.getValue().insuredId());
        assertEquals("员工离职", captor.getValue().reason());
        assertEquals("TENANT_001", captor.getValue().tenantId());
    }
}
