package com.titanium.policy.query.service;

import java.util.List;

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

    /**
     * 多条件分页查询投保单列表
     *
     * @param insuranceNo 投保单编号（模糊，可空）
     * @param holderId    投保人ID（精确，可空）
     * @param status      状态枚举 name（可空）
     * @param tenantId    租户ID
     * @param page        页码（从0开始）
     * @param size        每页条数
     * @return 投保单查询结果列表
     */
    List<InsuranceQueryResult> findInsurancesByConditions(String insuranceNo, String holderId,
                                                          String status, String tenantId, int page, int size);
}
