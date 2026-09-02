package com.titanium.policy.application.query;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.titanium.metadata.enums.customer.CustomerEnum.InsuranceRole;
import com.titanium.policy.query.result.PolicyClauseQueryResult;
import com.titanium.policy.query.result.PolicyCoverageQueryResult;
import com.titanium.policy.query.result.PolicyFullDetailQueryResult;
import com.titanium.policy.query.result.PolicyProductQueryResult;
import com.titanium.policy.query.result.PolicyQueryResult;
import com.titanium.policy.query.result.PolicySubjectQueryResult;
import com.titanium.policy.query.service.PolicyLineQueryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单险种段族读用例入口（application/query）
 * <p>
 * web 与 api provider 的唯一读入口。本门面只表达「要查什么」，查询实现（多表聚合、按段分组、
 * 按角色反查）内聚在读侧 {@link PolicyLineQueryService}（规约 §3.4.9 ④）。
 * </p>
 * <p>
 * 🔴 <b>绕 QueryGateway 直调读侧服务</b>：`queryGateway.query(q, List.class)` 对集合返回类型的
 * 泛型推断会退化为 {@code InstanceResponseType}，运行时抛 {@code NoHandlerForQueryException}
 * （同类问题已在多个域出现，见项目踩坑记录）。读侧 QueryHandler 保留供跨服务查询总线调用，
 * 本域内部直调 service 更稳且省一次序列化。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyLineAppQueryService {

    private final PolicyLineQueryService policyLineQueryService;

    /**
     * 保单全景查询（后台详情页：一次拿全九个维度）。
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 保单全景；保单不存在时返回空
     */
    public Optional<PolicyFullDetailQueryResult> findFullDetail(String policyId, String tenantId) {
        return policyLineQueryService.findFullDetail(policyId, tenantId);
    }

    /**
     * 查询保单险种段清单。
     *
     * @param policyId    保单ID
     * @param withDetails 是否装配段内条款/标的/责任明细
     * @param tenantId    租户ID
     * @return 险种段列表
     */
    public List<PolicyProductQueryResult> findLines(String policyId, boolean withDetails, String tenantId) {
        return policyLineQueryService.findLines(policyId, withDetails, tenantId);
    }

    /**
     * 查询保单保险责任清单（供理赔域定责）。
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 责任列表
     */
    public List<PolicyCoverageQueryResult> findCoverages(String policyId, String tenantId) {
        return policyLineQueryService.findCoverages(policyId, tenantId);
    }

    /**
     * 查询保单标的清单。
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 标的列表
     */
    public List<PolicySubjectQueryResult> findSubjects(String policyId, String tenantId) {
        return policyLineQueryService.findSubjects(policyId, tenantId);
    }

    /**
     * 查询保单条款快照清单（claim 域责任校验的条款定位来源）。
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 条款快照列表
     */
    public List<PolicyClauseQueryResult> findClauses(String policyId, String tenantId) {
        return policyLineQueryService.findClauses(policyId, tenantId);
    }

    /**
     * 按客户与保险角色查询其名下保单。
     *
     * @param customerId 客户ID
     * @param role       保险角色（为 null 时返回投保人/被保险人/受益人三种角色的并集）
     * @param tenantId   租户ID
     * @param page       页码（0 起）
     * @param size       每页条数
     * @return 保单列表
     */
    public List<PolicyQueryResult> findByCustomerRole(String customerId, InsuranceRole role, String tenantId, int page,
                                                      int size) {
        return policyLineQueryService.findByCustomerRole(customerId, role, tenantId, page, size);
    }
}
