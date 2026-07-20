package com.titanium.policy.query.service;

import java.util.List;

import com.titanium.policy.query.result.PolicyEndorsementQueryResult;

/**
 * 保单批改历史查询服务（CQRS 读侧）
 * <p>
 * 提供批改流水读模型查询，数据来源为事件投影维护的 {@code t_policy_endorsement_view}。
 * </p>
 */
public interface PolicyEndorsementQueryService {

    /**
     * 查询保单的批改历史清单
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 批改历史查询结果列表
     */
    List<PolicyEndorsementQueryResult> findEndorsementsByPolicyId(String policyId, String tenantId);
}
