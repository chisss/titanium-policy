package com.titanium.policy.query.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.titanium.policy.query.mapper.PolicyLineQueryMapper;
import com.titanium.policy.query.repository.PolicyBeneficiaryViewRepository;
import com.titanium.policy.query.repository.PolicyInsuredViewRepository;
import com.titanium.policy.query.result.PolicyBeneficiaryQueryResult;
import com.titanium.policy.query.result.PolicyInsuredQueryResult;
import com.titanium.policy.query.view.PolicyBeneficiaryView;
import com.titanium.policy.query.view.PolicyInsuredView;

class PolicyPartyQueryServiceImplTest {

    @Test
    void exposesBeneficiaryGenderAndPhoneFromSnapshot() {
        PolicyInsuredViewRepository insuredRepository = mock(PolicyInsuredViewRepository.class);
        PolicyBeneficiaryViewRepository beneficiaryRepository = mock(PolicyBeneficiaryViewRepository.class);
        PolicyBeneficiaryView view = new PolicyBeneficiaryView();
        view.setId("BEN-001");
        view.setPolicyId("POLICY_001");
        view.setTenantId("TENANT_001");
        view.setGender("FEMALE");
        view.setPhone("13900139000");
        when(beneficiaryRepository.findByPolicyIdAndTenantId("POLICY_001", "TENANT_001"))
                .thenReturn(List.of(view));

        PolicyBeneficiaryQueryResult result = new PolicyPartyQueryServiceImpl(insuredRepository,
                beneficiaryRepository, Mappers.getMapper(PolicyLineQueryMapper.class))
                .findBeneficiariesByPolicyId("POLICY_001", "TENANT_001").getFirst();

        assertEquals("FEMALE", result.getGender());
        assertEquals("13900139000", result.getPhone());
        assertEquals("BEN-001", result.getBeneficiaryId());
    }

    @Test
    void exposesInsuredIdFromRowIdentity() {
        PolicyInsuredViewRepository insuredRepository = mock(PolicyInsuredViewRepository.class);
        PolicyInsuredView view = new PolicyInsuredView();
        view.setId("INSURED_001");
        view.setPolicyId("POLICY_001");
        view.setTenantId("TENANT_001");
        view.setInsuredName("张三");
        when(insuredRepository.findByPolicyIdAndTenantId("POLICY_001", "TENANT_001"))
                .thenReturn(List.of(view));

        PolicyInsuredQueryResult result = new PolicyPartyQueryServiceImpl(insuredRepository,
                mock(PolicyBeneficiaryViewRepository.class), Mappers.getMapper(PolicyLineQueryMapper.class))
                .findInsuredByPolicyId("POLICY_001", "TENANT_001").getFirst();

        // 增减员端点的定位键：读模型行标识必须经参与方查询服务完整还原为 insuredId。
        // 🔴 本用例锁死「服务层走 PolicyLineQueryMapper」——若服务改回手工 set，臂 E 注入
        //    mapper 不再影响本路径，失败集合缺失本用例即判红（g12-06 分叉事故防再犯）。
        assertEquals("INSURED_001", result.getInsuredId());
        assertEquals("张三", result.getInsuredName());
    }
}
