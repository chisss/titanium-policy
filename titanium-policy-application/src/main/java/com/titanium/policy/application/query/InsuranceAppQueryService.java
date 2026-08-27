package com.titanium.policy.application.query;

import java.util.List;
import java.util.Optional;

import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.titanium.policy.query.query.FindInsuranceByIdQuery;
import com.titanium.policy.query.query.FindInsuranceByNoQuery;
import com.titanium.policy.query.query.FindInsurancesByConditionQuery;
import com.titanium.policy.query.result.InsuranceQueryResult;
import com.titanium.policy.query.service.InsuranceQueryService;

import lombok.RequiredArgsConstructor;

/**
 * 投保单查询服务（CQRS 读侧入口）
 * <p>
 * 读写分离落地：经 {@link QueryGateway} 派发查询到读侧 {@code InsuranceQueryHandler}，
 * 查询 {@code InsuranceView} 读模型，<b>不再回退到写模型聚合 {@code Insurance}</b>。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class InsuranceAppQueryService {

    private final QueryGateway          queryGateway;

    private final InsuranceQueryService insuranceQueryService;

    /**
     * 根据ID查询投保单（读模型）
     *
     * @param insuranceId 投保单ID
     * @param tenantId 租户ID
     * @return 投保单查询结果，不存在时为空
     */
    public Optional<InsuranceQueryResult> findById(String insuranceId, String tenantId) {
        InsuranceQueryResult result = queryGateway.query(new FindInsuranceByIdQuery(insuranceId, tenantId),
                ResponseTypes.instanceOf(InsuranceQueryResult.class)).join();
        return Optional.ofNullable(result);
    }

    /**
     * 根据编号查询投保单（读模型）
     *
     * @param insuranceNo 投保单编号
     * @param tenantId 租户ID
     * @return 投保单查询结果，不存在时为空
     */
    public Optional<InsuranceQueryResult> findByInsuranceNo(String insuranceNo, String tenantId) {
        InsuranceQueryResult result = queryGateway.query(new FindInsuranceByNoQuery(insuranceNo, tenantId),
                ResponseTypes.instanceOf(InsuranceQueryResult.class)).join();
        return Optional.ofNullable(result);
    }

    /**
     * 多条件分页查询投保单列表（读模型）
     *
     * @param insuranceNo 投保单编号（模糊，可空）
     * @param holderId    投保人ID（精确，可空）
     * @param productId   主险产品ID（精确，可空）
     * @param status      状态枚举 name（可空）
     * @param tenantId    租户ID
     * @param page        页码（从0开始）
     * @param size        每页条数
     * @return 投保单查询结果列表
     */
    public List<InsuranceQueryResult> findByConditions(String insuranceNo, String holderId, String productId,
                                                       String status, String tenantId, int page, int size) {
        return queryGateway.query(
                new FindInsurancesByConditionQuery(insuranceNo, holderId, productId, status, tenantId, page, size),
                ResponseTypes.multipleInstancesOf(InsuranceQueryResult.class)).join();
    }

    /**
     * 多条件分页查询投保单，并保留总条数等分页元数据。
     */
    public Page<InsuranceQueryResult> findPageByConditions(String insuranceNo, String holderId, String productId,
                                                           String status, String tenantId, int page, int size) {
        // Axon 4.10 无法以 InstanceResponseType 匹配实现 Iterable 的 Page，分页查询直调读模型服务。
        return insuranceQueryService.findInsurancesPageByConditions(insuranceNo, holderId, productId, status,
                tenantId, page, size);
    }
}
