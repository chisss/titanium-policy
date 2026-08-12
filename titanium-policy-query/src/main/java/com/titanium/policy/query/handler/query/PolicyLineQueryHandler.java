package com.titanium.policy.query.handler.query;

import java.util.List;
import java.util.Optional;

import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import com.titanium.policy.query.query.FindPoliciesByCustomerRoleQuery;
import com.titanium.policy.query.query.FindPolicyCoveragesQuery;
import com.titanium.policy.query.query.FindPolicyFullDetailQuery;
import com.titanium.policy.query.query.FindPolicyLinesQuery;
import com.titanium.policy.query.query.FindPolicySubjectsQuery;
import com.titanium.policy.query.result.PolicyCoverageQueryResult;
import com.titanium.policy.query.result.PolicyFullDetailQueryResult;
import com.titanium.policy.query.result.PolicyProductQueryResult;
import com.titanium.policy.query.result.PolicyQueryResult;
import com.titanium.policy.query.result.PolicySubjectQueryResult;
import com.titanium.policy.query.service.PolicyLineQueryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单险种段族查询处理器（Axon QueryBus 入口）
 * <p>
 * 承接一单多险的五个读侧查询，委托 {@link PolicyLineQueryService} 执行——查询实现（多表聚合、
 * 按段分组、按角色反查）内聚在 service，本处理器只做查询派发。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyLineQueryHandler {

    private final PolicyLineQueryService policyLineQueryService;

    /**
     * 保单全景查询：一次返回保单主体 + 险种段（含条款/标的/责任）+ 参与方 + 收费。
     *
     * @param query 全景查询
     * @return 保单全景；保单不存在时返回空
     */
    @QueryHandler
    public Optional<PolicyFullDetailQueryResult> handle(FindPolicyFullDetailQuery query) {
        return policyLineQueryService.findFullDetail(query.policyId(), query.tenantId());
    }

    /**
     * 查询保单险种段清单。
     *
     * @param query 段清单查询
     * @return 险种段列表
     */
    @QueryHandler
    public List<PolicyProductQueryResult> handle(FindPolicyLinesQuery query) {
        return policyLineQueryService.findLines(query.policyId(), query.withDetails(), query.tenantId());
    }

    /**
     * 查询保单保险责任清单（理赔域定责依据）。
     *
     * @param query 责任查询
     * @return 责任列表
     */
    @QueryHandler
    public List<PolicyCoverageQueryResult> handle(FindPolicyCoveragesQuery query) {
        return policyLineQueryService.findCoverages(query.policyId(), query.tenantId());
    }

    /**
     * 查询保单标的清单（车险多车、企财多分项）。
     *
     * @param query 标的查询
     * @return 标的列表
     */
    @QueryHandler
    public List<PolicySubjectQueryResult> handle(FindPolicySubjectsQuery query) {
        return policyLineQueryService.findSubjects(query.policyId(), query.tenantId());
    }

    /**
     * 按客户与保险角色查询其名下保单。
     *
     * @param query 客户角色查询
     * @return 保单列表
     */
    @QueryHandler
    public List<PolicyQueryResult> handle(FindPoliciesByCustomerRoleQuery query) {
        return policyLineQueryService.findByCustomerRole(query.customerId(), query.role(), query.tenantId(),
                query.page(), query.size());
    }
}
