package com.titanium.policy.query.handler;

import java.util.List;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import com.titanium.policy.query.entity.PolicyQueryResult;
import com.titanium.policy.query.query.FindPoliciesByCustomerQuery;
import com.titanium.policy.query.query.FindPoliciesByMultipleConditionsQuery;
import com.titanium.policy.query.query.FindPolicyByIdQuery;
import com.titanium.policy.query.service.PolicyQueryService;

import lombok.AllArgsConstructor;

/**
 * 保单查询处理器
 * <p>
 * 处理各种保单查询请求
 * </p>
 */
@Component
@AllArgsConstructor
@ProcessingGroup("policy-query-group")
public class PolicyQueryHandler {

    private final PolicyQueryService policyQueryService;

    /**
     * 根据ID查询保单
     *
     * @param query 查询条件
     * @return 保单查询结果
     */
    @QueryHandler
    public PolicyQueryResult handle(FindPolicyByIdQuery query) {
        return policyQueryService.findPolicyById(query.policyId(), query.tenantId());
    }

    /**
     * 根据客户ID查询保单
     *
     * @param query 查询条件
     * @return 保单查询结果列表
     */
    @QueryHandler
    public List<PolicyQueryResult> handle(FindPoliciesByCustomerQuery query) {
        return policyQueryService.findPoliciesByCustomerId(query.customerId(), query.tenantId(), query.page(),
                query.size());
    }

    /**
     * 根据多条件查询保单
     *
     * @param query 查询条件
     * @return 保单查询结果列表
     */
    @QueryHandler
    public List<PolicyQueryResult> handle(FindPoliciesByMultipleConditionsQuery query) {
        return policyQueryService.findPoliciesByMultipleConditions(query.policyNo(), query.policyHolderName(),
                query.insuredName(), query.productCode(), query.status(), query.effectiveDateStart(),
                query.effectiveDateEnd(), query.expiryDateStart(), query.expiryDateEnd(), query.tenantId(),
                query.page(), query.size());
    }
}
