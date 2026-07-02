package com.titanium.policy.query.service;

import com.titanium.policy.query.result.InsuranceQueryResult;

/**
 * 投保单查询服务
 * <p>
 * 提供投保单读模型查询功能（CQRS 读侧）。
 * </p>
 */
public interface InsuranceQueryService {

    /**
     * 根据ID查询投保单
     *
     * @param insuranceId 投保单ID
     * @param tenantId 租户ID
     * @return 投保单查询结果，不存在时为 null
     */
    InsuranceQueryResult findInsuranceById(String insuranceId, String tenantId);

    /**
     * 根据编号查询投保单
     *
     * @param insuranceNo 投保单编号
     * @param tenantId 租户ID
     * @return 投保单查询结果，不存在时为 null
     */
    InsuranceQueryResult findInsuranceByNo(String insuranceNo, String tenantId);
}
