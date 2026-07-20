package com.titanium.policy.query.handler.query;

import java.util.List;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import com.titanium.policy.query.query.FindEndorsementsByPolicyIdQuery;
import com.titanium.policy.query.result.PolicyEndorsementQueryResult;
import com.titanium.policy.query.service.PolicyEndorsementQueryService;

import lombok.AllArgsConstructor;

/**
 * 保单批改历史查询处理器（CQRS 读侧）
 * <p>
 * 处理批改流水查询请求，转发至 {@link PolicyEndorsementQueryService}。
 * </p>
 */
@Component
@AllArgsConstructor
@ProcessingGroup("policy-query-group")
public class PolicyEndorsementQueryHandler {

    private final PolicyEndorsementQueryService policyEndorsementQueryService;

    /**
     * 查询保单批改历史清单
     *
     * @param query 查询条件
     * @return 批改历史查询结果列表
     */
    @QueryHandler
    public List<PolicyEndorsementQueryResult> handle(FindEndorsementsByPolicyIdQuery query) {
        return policyEndorsementQueryService.findEndorsementsByPolicyId(query.policyId(), query.tenantId());
    }
}
