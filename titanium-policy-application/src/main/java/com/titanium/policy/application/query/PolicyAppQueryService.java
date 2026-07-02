package com.titanium.policy.application.query;

import java.util.List;
import java.util.Optional;

import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import com.titanium.policy.query.query.FindPoliciesByCustomerQuery;
import com.titanium.policy.query.query.FindPolicyByIdQuery;
import com.titanium.policy.query.result.PolicyQueryResult;

import jakarta.annotation.Resource;

/**
 * 保单查询服务（CQRS 读侧入口）
 * <p>
 * 读写分离落地：经 {@link QueryGateway} 派发查询到读侧 {@code PolicyQueryHandler}，
 * 查询 {@code PolicyView} 读模型，<b>不再回退到写模型聚合 {@code Policy}</b>。
 * 读侧与写侧彻底解耦，查询走独立优化的读模型表 {@code t_policy_view}。
 * </p>
 */
@Service
public class PolicyAppQueryService {

    @Resource
    private QueryGateway queryGateway;

    /**
     * 根据ID查询保单（读模型）
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 保单查询结果，不存在时为空
     */
    public Optional<PolicyQueryResult> findById(String policyId, String tenantId) {
        PolicyQueryResult result = queryGateway
                .query(new FindPolicyByIdQuery(policyId, tenantId), ResponseTypes.instanceOf(PolicyQueryResult.class))
                .join();
        return Optional.ofNullable(result);
    }

    /**
     * 根据客户ID分页查询保单（读模型）
     *
     * @param customerId 客户ID
     * @param tenantId 租户ID
     * @param page 页码（从0开始）
     * @param size 每页条数
     * @return 保单查询结果列表
     */
    public List<PolicyQueryResult> findByCustomerId(String customerId, String tenantId, int page, int size) {
        return queryGateway.query(new FindPoliciesByCustomerQuery(customerId, tenantId, page, size),
                ResponseTypes.multipleInstancesOf(PolicyQueryResult.class)).join();
    }
}
