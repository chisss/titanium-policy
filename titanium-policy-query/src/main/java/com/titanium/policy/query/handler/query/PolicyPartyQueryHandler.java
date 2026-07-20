package com.titanium.policy.query.handler.query;

import java.util.List;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import com.titanium.policy.query.query.FindBeneficiariesByPolicyIdQuery;
import com.titanium.policy.query.query.FindInsuredByPolicyIdQuery;
import com.titanium.policy.query.result.PolicyBeneficiaryQueryResult;
import com.titanium.policy.query.result.PolicyInsuredQueryResult;
import com.titanium.policy.query.service.PolicyPartyQueryService;

import lombok.AllArgsConstructor;

/**
 * 保单参与方查询处理器（CQRS 读侧）
 * <p>
 * 处理被保险人和受益人的查询请求，转发至 {@link PolicyPartyQueryService}。
 * </p>
 */
@Component
@AllArgsConstructor
@ProcessingGroup("policy-query-group")
public class PolicyPartyQueryHandler {

    private final PolicyPartyQueryService policyPartyQueryService;

    /**
     * 查询保单被保险人清单
     *
     * @param query 查询条件
     * @return 被保险人查询结果列表
     */
    @QueryHandler
    public List<PolicyInsuredQueryResult> handle(FindInsuredByPolicyIdQuery query) {
        return policyPartyQueryService.findInsuredByPolicyId(query.policyId(), query.tenantId());
    }

    /**
     * 查询保单受益人清单
     *
     * @param query 查询条件
     * @return 受益人查询结果列表
     */
    @QueryHandler
    public List<PolicyBeneficiaryQueryResult> handle(FindBeneficiariesByPolicyIdQuery query) {
        return policyPartyQueryService.findBeneficiariesByPolicyId(query.policyId(), query.tenantId());
    }
}
