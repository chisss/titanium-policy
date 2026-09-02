package com.titanium.policy.query.service;

import java.util.List;
import java.util.Optional;

import com.titanium.metadata.enums.customer.CustomerEnum.InsuranceRole;
import com.titanium.policy.query.result.PolicyClauseQueryResult;
import com.titanium.policy.query.result.PolicyCollectionQueryResult;
import com.titanium.policy.query.result.PolicyCoverageQueryResult;
import com.titanium.policy.query.result.PolicyFullDetailQueryResult;
import com.titanium.policy.query.result.PolicyProductQueryResult;
import com.titanium.policy.query.result.PolicyQueryResult;
import com.titanium.policy.query.result.PolicySubjectQueryResult;

/**
 * 保单险种段族读模型查询服务（L2/L2.5/L3/L4 + 收费 + 全景）
 * <p>
 * 一单多险的读侧入口。复杂查询（多表聚合、按角色反查）内聚于本服务实现，application 层的
 * {@code *AppQueryService} 只表达「要查什么」，不含查询实现细节（规约 §3.4.9 ④）。
 * </p>
 */
public interface PolicyLineQueryService {

    /**
     * 保单全景查询：一次返回保单主体 + 险种段（含条款/标的/责任）+ 参与方 + 收费。
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 保单全景；保单不存在时返回空
     */
    Optional<PolicyFullDetailQueryResult> findFullDetail(String policyId, String tenantId);

    /**
     * 查询保单险种段清单。
     *
     * @param policyId    保单ID
     * @param withDetails 是否装配段内条款/标的/责任明细
     * @param tenantId    租户ID
     * @return 险种段列表（按段序号升序）
     */
    List<PolicyProductQueryResult> findLines(String policyId, boolean withDetails, String tenantId);

    /**
     * 查询保单全部保险责任（跨险种段）。
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 责任列表
     */
    List<PolicyCoverageQueryResult> findCoverages(String policyId, String tenantId);

    /**
     * 查询保单条款快照清单（跨险种段，签发即冻结）。
     * <p>
     * 下游（如 claim 域责任校验）凭条款ID穿透 clause 域取保险责任。
     * </p>
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 条款快照列表（无条款时为空列表）
     */
    List<PolicyClauseQueryResult> findClauses(String policyId, String tenantId);

    /**
     * 查询保单全部标的（跨险种段）。
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 标的列表
     */
    List<PolicySubjectQueryResult> findSubjects(String policyId, String tenantId);

    /**
     * 查询保单收费信息。
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 收费信息；未开单时返回空
     */
    Optional<PolicyCollectionQueryResult> findCollection(String policyId, String tenantId);

    /**
     * 按客户与保险角色查询其名下保单。
     * <p>
     * 投保人查 {@code t_policy_view.policy_holder_id}；被保险人查 {@code t_policy_insured}；
     * 受益人查 {@code t_policy_beneficiary}。角色为 null 时返回三者并集（按保单去重）。
     * </p>
     *
     * @param customerId 客户ID
     * @param role       保险角色（可为 null）
     * @param tenantId   租户ID
     * @param page       页码（0 起）
     * @param size       每页条数
     * @return 保单列表
     */
    List<PolicyQueryResult> findByCustomerRole(String customerId, InsuranceRole role, String tenantId, int page,
                                               int size);
}
