package com.titanium.policy.query.service;

import java.util.List;

import com.titanium.policy.query.result.PolicyBeneficiaryQueryResult;
import com.titanium.policy.query.result.PolicyInsuredQueryResult;

/**
 * 保单参与方查询服务（CQRS 读侧）
 * <p>
 * 提供被保险人、受益人读模型查询，数据来源为事件投影维护的 {@code t_policy_insured} 和
 * {@code t_policy_beneficiary}。
 * </p>
 */
public interface PolicyPartyQueryService {

    /**
     * 查询保单的被保险人清单
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 被保险人查询结果列表
     */
    List<PolicyInsuredQueryResult> findInsuredByPolicyId(String policyId, String tenantId);

    /**
     * 查询保单的受益人清单
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 受益人查询结果列表
     */
    List<PolicyBeneficiaryQueryResult> findBeneficiariesByPolicyId(String policyId, String tenantId);
}
