package com.titanium.policy.application.query;

import java.util.Optional;

import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import com.titanium.policy.query.query.FindInsuranceByIdQuery;
import com.titanium.policy.query.query.FindInsuranceByNoQuery;
import com.titanium.policy.query.result.InsuranceQueryResult;

import jakarta.annotation.Resource;

/**
 * 投保单查询服务（CQRS 读侧入口）
 * <p>
 * 读写分离落地：经 {@link QueryGateway} 派发查询到读侧 {@code InsuranceQueryHandler}，
 * 查询 {@code InsuranceView} 读模型，<b>不再回退到写模型聚合 {@code Insurance}</b>。
 * </p>
 */
@Service
public class InsuranceAppQueryService {

    @Resource
    private QueryGateway queryGateway;

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
}
