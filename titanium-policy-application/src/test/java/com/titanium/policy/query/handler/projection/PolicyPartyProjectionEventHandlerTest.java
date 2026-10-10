package com.titanium.policy.query.handler.projection;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;
import com.titanium.metadata.enums.customer.CustomerEnum.IdCardType;
import com.titanium.metadata.enums.policy.BeneficiaryType;
import com.titanium.policy.common.enums.FamilyRelation;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.event.InsuredMemberAddedEvent;
import com.titanium.policy.event.InsuredMemberRemovedEvent;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.query.repository.PolicyBeneficiaryViewRepository;
import com.titanium.policy.query.repository.PolicyInsuredViewRepository;
import com.titanium.policy.query.view.PolicyBeneficiaryView;
import com.titanium.policy.query.view.PolicyInsuredView;

class PolicyPartyProjectionEventHandlerTest {

    @Test
    void projectsRelationToHolderIntoInsuredSnapshot() {
        PolicyInsuredViewRepository insuredRepository = mock(PolicyInsuredViewRepository.class);
        PolicyBeneficiaryViewRepository beneficiaryRepository = mock(PolicyBeneficiaryViewRepository.class);
        when(insuredRepository.save(any(PolicyInsuredView.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InsuredPartyList.InsuredInfo insured = new InsuredPartyList.InsuredInfo("CUSTOMER_001", "INSURED_001",
                "张三", IdCardType.CHINA_ID_CARD, "110101199001011234", 35, CustomerGender.MALE, "SELF", null);
        InsuredPartyList parties = new InsuredPartyList("PARTIES_001",
                new InsuredPartyList.HolderInfo("CUSTOMER_001", "HOLDER_001", "张三",
                        IdCardType.CHINA_ID_CARD, "110101199001011234", "13800138000"),
                List.of(insured), List.of());
        PolicyCreatedEvent event = policyCreatedEvent(parties);

        new PolicyPartyProjectionEventHandler(insuredRepository, beneficiaryRepository).on(event);

        ArgumentCaptor<PolicyInsuredView> captor = ArgumentCaptor.forClass(PolicyInsuredView.class);
        verify(insuredRepository).save(captor.capture());
        assertEquals("SELF", captor.getValue().getRelation());
        assertEquals("CUSTOMER_001", captor.getValue().getCustomerId());
        // 🔴 读模型主键即保全集合字段的对象标识（聚合内 insuredId）：写侧执行器按该标识定位被保险人元素，
        // 两侧派生规则必须同源，否则受理成功的保全项会在生效环节失败关闭
        assertEquals("INSURED_001", captor.getValue().getId());
    }

    @Test
    void projectsBeneficiaryGenderAndPhoneIntoSnapshot() {
        PolicyInsuredViewRepository insuredRepository = mock(PolicyInsuredViewRepository.class);
        PolicyBeneficiaryViewRepository beneficiaryRepository = mock(PolicyBeneficiaryViewRepository.class);
        when(beneficiaryRepository.save(any(PolicyBeneficiaryView.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InsuredPartyList.BeneficiaryInfo beneficiary = new InsuredPartyList.BeneficiaryInfo("CUSTOMER_002",
                "BENEFICIARY_001", "李四", IdCardType.CHINA_ID_CARD, "110101199202022222",
                CustomerGender.FEMALE, "13900139000", BeneficiaryType.DEATH, 1, 1.0d);
        InsuredPartyList parties = new InsuredPartyList("PARTIES_001",
                new InsuredPartyList.HolderInfo("CUSTOMER_001", "HOLDER_001", "张三",
                        IdCardType.CHINA_ID_CARD, "110101199001011234", "13800138000"),
                List.of(), List.of(beneficiary));

        new PolicyPartyProjectionEventHandler(insuredRepository, beneficiaryRepository)
                .on(policyCreatedEvent(parties));

        ArgumentCaptor<PolicyBeneficiaryView> captor = ArgumentCaptor.forClass(PolicyBeneficiaryView.class);
        verify(beneficiaryRepository).save(captor.capture());
        assertEquals("FEMALE", captor.getValue().getGender());
        assertEquals("13900139000", captor.getValue().getPhone());
    }

    private PolicyCreatedEvent policyCreatedEvent(InsuredPartyList parties) {
        return new PolicyCreatedEvent("POLICY_001", null, null, null, null, null, null, "BIZ_001", null, null,
                null, null, List.of(), null, null, null, null, parties, null, "TENANT_001");
    }

    @Test
    void projectsAddedMemberReusingInsuredIdAsRowIdentity() {
        PolicyInsuredViewRepository insuredRepository = mock(PolicyInsuredViewRepository.class);
        PolicyBeneficiaryViewRepository beneficiaryRepository = mock(PolicyBeneficiaryViewRepository.class);
        when(insuredRepository.save(any(PolicyInsuredView.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(insuredRepository.findByPolicyIdAndTenantId("POLICY_001", "TENANT_001")).thenReturn(List.of());
        when(insuredRepository.findById("INSURED_001")).thenReturn(Optional.empty());
        InsuredPartyList.InsuredInfo member = new InsuredPartyList.InsuredInfo("CUSTOMER_001", "INSURED_001",
                "张三", IdCardType.CHINA_ID_CARD, "110101199001011234", 35, CustomerGender.MALE, "SELF", null);

        new PolicyPartyProjectionEventHandler(insuredRepository, beneficiaryRepository)
                .on(new InsuredMemberAddedEvent("POLICY_001", member, FamilyRelation.CHILD,
                        LocalDateTime.of(2026, 10, 10, 12, 0), "operator-1", "TENANT_001"));

        ArgumentCaptor<PolicyInsuredView> captor = ArgumentCaptor.forClass(PolicyInsuredView.class);
        verify(insuredRepository).save(captor.capture());
        // 合规成员标识直接复用为读模型行标识 ⇒ 减员端点（DELETE /insured-members/{insuredId}）可闭环命中
        assertEquals("INSURED_001", captor.getValue().getId());
        // 🔴 家庭险：事件携带的家庭关系必须覆盖成员自带值（与聚合 on(InsuredMemberAddedEvent) 同法重建）
        assertEquals("CHILD", captor.getValue().getFamilyRelation());
        assertEquals("TENANT_001", captor.getValue().getTenantId());
    }

    @Test
    void skipsAddedMemberWhenRowAlreadyProjected() {
        PolicyInsuredViewRepository insuredRepository = mock(PolicyInsuredViewRepository.class);
        PolicyBeneficiaryViewRepository beneficiaryRepository = mock(PolicyBeneficiaryViewRepository.class);
        PolicyInsuredView existing = new PolicyInsuredView();
        existing.setId("INSURED_001");
        when(insuredRepository.findById("INSURED_001")).thenReturn(Optional.of(existing));
        InsuredPartyList.InsuredInfo member = new InsuredPartyList.InsuredInfo("CUSTOMER_001", "INSURED_001",
                "张三", IdCardType.CHINA_ID_CARD, "110101199001011234", 35, CustomerGender.MALE, "SELF", null);

        new PolicyPartyProjectionEventHandler(insuredRepository, beneficiaryRepository)
                .on(new InsuredMemberAddedEvent("POLICY_001", member, null,
                        LocalDateTime.of(2026, 10, 10, 12, 0), "operator-1", "TENANT_001"));

        // 事件重放幂等：同一成员标识的行已存在即跳过，不产生重复行
        verify(insuredRepository, never()).save(any(PolicyInsuredView.class));
    }

    @Test
    void removesInsuredRowOnMemberRemovedEvent() {
        PolicyInsuredViewRepository insuredRepository = mock(PolicyInsuredViewRepository.class);
        PolicyBeneficiaryViewRepository beneficiaryRepository = mock(PolicyBeneficiaryViewRepository.class);
        PolicyInsuredView existing = new PolicyInsuredView();
        existing.setId("INSURED_001");
        existing.setTenantId("TENANT_001");
        when(insuredRepository.findById("INSURED_001")).thenReturn(Optional.of(existing));

        new PolicyPartyProjectionEventHandler(insuredRepository, beneficiaryRepository)
                .on(new InsuredMemberRemovedEvent("POLICY_001", "INSURED_001", "员工离职",
                        LocalDateTime.of(2026, 10, 10, 12, 0), "operator-1", "TENANT_001"));

        verify(insuredRepository).delete(existing);
    }

    @Test
    void skipsRemovalWhenProjectionRowMissing() {
        PolicyInsuredViewRepository insuredRepository = mock(PolicyInsuredViewRepository.class);
        PolicyBeneficiaryViewRepository beneficiaryRepository = mock(PolicyBeneficiaryViewRepository.class);
        when(insuredRepository.findById("INSURED_999")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> new PolicyPartyProjectionEventHandler(insuredRepository, beneficiaryRepository)
                .on(new InsuredMemberRemovedEvent("POLICY_001", "INSURED_999", null,
                        LocalDateTime.of(2026, 10, 10, 12, 0), "operator-1", "TENANT_001")));
        // 投影只做最终一致维护：单行缺失不得抛出（否则会卡住 policy-query-group 的事件位点）
        verify(insuredRepository, never()).delete(any(PolicyInsuredView.class));
    }
}
