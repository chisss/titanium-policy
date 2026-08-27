package com.titanium.policy.query.handler.query;

import java.util.List;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import com.titanium.policy.query.query.FindInsuranceByIdQuery;
import com.titanium.policy.query.query.FindInsuranceByNoQuery;
import com.titanium.policy.query.query.FindInsurancesByConditionQuery;
import com.titanium.policy.query.result.InsuranceQueryResult;
import com.titanium.policy.query.service.InsuranceQueryService;

import lombok.AllArgsConstructor;

/**
 * 投保单查询处理器
 * <p>
 * 处理投保单读模型查询请求，委托 {@link InsuranceQueryService} 查询 {@code t_insurance_view}。
 * </p>
 */
@Component
@AllArgsConstructor
@ProcessingGroup("policy-query-group")
public class InsuranceQueryHandler {

    private final InsuranceQueryService insuranceQueryService;

    /**
     * 根据ID查询投保单
     *
     * @param query 查询条件
     * @return 投保单查询结果
     */
    @QueryHandler
    public InsuranceQueryResult handle(FindInsuranceByIdQuery query) {
        return insuranceQueryService.findInsuranceById(query.insuranceId(), query.tenantId());
    }

    /**
     * 根据编号查询投保单
     *
     * @param query 查询条件
     * @return 投保单查询结果
     */
    @QueryHandler
    public InsuranceQueryResult handle(FindInsuranceByNoQuery query) {
        return insuranceQueryService.findInsuranceByNo(query.insuranceNo(), query.tenantId());
    }

    /**
     * 多条件分页查询投保单列表
     *
     * @param query 查询条件
     * @return 投保单查询结果列表
     */
    @QueryHandler
    public List<InsuranceQueryResult> handle(FindInsurancesByConditionQuery query) {
        return insuranceQueryService.findInsurancesByConditions(query.insuranceNo(), query.holderId(),
                query.productId(), query.status(), query.tenantId(), query.page(), query.size());
    }
}
