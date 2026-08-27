package com.titanium.policy.application.query;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.titanium.policy.query.query.FindBeneficiariesByPolicyIdQuery;
import com.titanium.policy.query.query.FindEndorsementsByPolicyIdQuery;
import com.titanium.policy.query.query.FindInsuredByPolicyIdQuery;
import com.titanium.policy.query.query.FindPoliciesByCustomerQuery;
import com.titanium.policy.query.query.FindPoliciesByMultipleConditionsQuery;
import com.titanium.policy.query.query.FindPolicyByIdQuery;
import com.titanium.policy.query.query.FindPolicyStatisticsQuery;
import com.titanium.policy.query.result.PolicyBeneficiaryQueryResult;
import com.titanium.policy.query.result.PolicyEndorsementQueryResult;
import com.titanium.policy.query.result.PolicyInsuredQueryResult;
import com.titanium.policy.query.result.PolicyMaintenanceCaseReferenceQueryResult;
import com.titanium.policy.query.result.PolicyMaintenanceSnapshotQueryResult;
import com.titanium.policy.query.result.PolicyQueryResult;
import com.titanium.policy.query.result.PolicyStatisticsResult;
import com.titanium.policy.query.service.PolicyQueryService;

import lombok.RequiredArgsConstructor;
// 读门面入参即读侧查询 record（FindXxxQuery），由 web 直接构造，见 docs/DDD-API层与Web层职责边界及协作规范.md

/**
 * 保单查询服务（CQRS 读侧入口）
 * <p>
 * 读写分离落地：经 {@link QueryGateway} 派发查询到读侧 {@code PolicyQueryHandler}，
 * 查询 {@code PolicyView} 读模型，<b>不再回退到写模型聚合 {@code Policy}</b>。
 * 读侧与写侧彻底解耦，查询走独立优化的读模型表 {@code t_policy_view}。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class PolicyAppQueryService {

    private final QueryGateway       queryGateway;

    private final PolicyQueryService policyQueryService;

    /**
     * 根据ID查询保单（读模型）
     * <p>
     * 读门面入参即读侧查询 record，由 web 适配器直接构造后传入，经 QueryGateway 派发到读侧处理器。
     * </p>
     *
     * @param query 保单按ID查询（含 policyId + tenantId）
     * @return 保单查询结果，不存在时为空
     */
    public Optional<PolicyQueryResult> findById(FindPolicyByIdQuery query) {
        PolicyQueryResult result = queryGateway
                .query(query, ResponseTypes.instanceOf(PolicyQueryResult.class))
                .join();
        return Optional.ofNullable(result);
    }

    /** 查询保全建案专用的 Policy 权威快照。 */
    public Optional<PolicyMaintenanceSnapshotQueryResult> findMaintenanceSnapshot(
            String policyId,
            String tenantId) {
        return Optional.ofNullable(policyQueryService.findMaintenanceSnapshot(policyId, tenantId));
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

    /**
     * 多条件分页查询保单（读模型）
     * <p>
     * 面向后台/端上组合检索：保单号/投保人姓名/被保险人姓名/产品编码/状态任意组合。读门面只表达「要查什么」，
     * 构造读侧查询 record 经 QueryGateway 派发到读侧处理器，查询 {@code PolicyView} 读模型。生效/止期区间
     * 由本重载暂不透出，统一置空由读侧按存在性忽略。
     * </p>
     *
     * @param policyNo 保单号（可空）
     * @param policyHolderName 投保人姓名（可空）
     * @param insuredName 被保险人姓名（可空）
     * @param productCode 产品编码（可空）
     * @param status 保单状态（可空）
     * @param tenantId 租户ID
     * @param page 页码（从0开始）
     * @param size 每页条数
     * @return 保单查询结果列表
     */
    public List<PolicyQueryResult> findByConditions(String policyNo, String policyHolderName, String insuredName,
                                                    String productCode, String status, String tenantId, int page,
                                                    int size) {
        return queryGateway.query(
                new FindPoliciesByMultipleConditionsQuery(policyNo, policyHolderName, insuredName, productCode, status,
                        null, null, null, null, tenantId, page, size),
                ResponseTypes.multipleInstancesOf(PolicyQueryResult.class)).join();
    }

    /**
     * 多条件分页查询保单，并保留总条数等分页元数据。
     */
    public Page<PolicyQueryResult> findPageByConditions(String policyNo, String policyHolderName, String insuredName,
                                                        String productCode, String status, String tenantId, int page,
                                                        int size) {
        // Axon 4.10 无法以 InstanceResponseType 匹配实现 Iterable 的 Page，分页查询直调读模型服务。
        return policyQueryService.findPoliciesPageByMultipleConditions(policyNo, policyHolderName, insuredName,
                productCode, status, null, null, null, null, tenantId, page, size);
    }

    /**
     * 查询保单受益人清单（读模型）
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 受益人查询结果列表
     */
    public List<PolicyBeneficiaryQueryResult> findBeneficiaries(String policyId, String tenantId) {
        return queryGateway.query(new FindBeneficiariesByPolicyIdQuery(policyId, tenantId),
                ResponseTypes.multipleInstancesOf(PolicyBeneficiaryQueryResult.class)).join();
    }

    /**
     * 查询保单被保险人清单（读模型）
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 被保险人查询结果列表
     */
    public List<PolicyInsuredQueryResult> findInsuredParties(String policyId, String tenantId) {
        return queryGateway.query(new FindInsuredByPolicyIdQuery(policyId, tenantId),
                ResponseTypes.multipleInstancesOf(PolicyInsuredQueryResult.class)).join();
    }

    /**
     * 查询保单批改历史清单（读模型）
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 批改历史查询结果列表
     */
    public List<PolicyEndorsementQueryResult> findEndorsements(String policyId, String tenantId) {
        return queryGateway.query(new FindEndorsementsByPolicyIdQuery(policyId, tenantId),
                ResponseTypes.multipleInstancesOf(PolicyEndorsementQueryResult.class)).join();
    }

    /**
     * 查询已在本保单落地的保全案件引用。
     * <p>
     * 数据来自租户隔离的批改投影，仅返回具有来源保全案件 ID 的批改记录，并按案件 ID 去重。
     * </p>
     */
    public List<PolicyMaintenanceCaseReferenceQueryResult> findMaintenanceCaseReferences(
            String policyId,
            String tenantId) {
        Map<String, PolicyMaintenanceCaseReferenceQueryResult> references = new LinkedHashMap<>();
        for (PolicyEndorsementQueryResult endorsement : findEndorsements(policyId, tenantId)) {
            String maintenanceId = endorsement.getSourceMaintenanceId();
            if (maintenanceId == null || maintenanceId.isBlank()) {
                continue;
            }
            references.putIfAbsent(maintenanceId, new PolicyMaintenanceCaseReferenceQueryResult(
                    maintenanceId,
                    endorsement.getEndorsementNo(),
                    endorsement.getUpdateType(),
                    endorsement.getPolicyVersion(),
                    endorsement.getEffectiveDate(),
                    endorsement.getEndorsedAt()));
        }
        return List.copyOf(references.values());
    }

    /**
     * 查询保单聚合统计（管理后台看板读入口）
     * <p>
     * 只表达「要查什么」，构造统计查询 record 经 QueryGateway 派发到读侧处理器，聚合 {@code PolicyView} 读模型。
     * 强制携带 {@code tenantId} 保证多租户隔离。
     * </p>
     *
     * @param tenantId 租户ID
     * @return 保单统计结果
     */
    public PolicyStatisticsResult getStatistics(String tenantId) {
        return queryGateway.query(new FindPolicyStatisticsQuery(tenantId),
                ResponseTypes.instanceOf(PolicyStatisticsResult.class)).join();
    }
}
