package com.titanium.policy.query.service;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.policy.query.result.PolicyQueryResult;

/**
 * 保单查询服务
 * <p>
 * 提供复杂的保单查询功能
 * </p>
 */
public interface PolicyQueryService {

    /**
     * 根据ID查询保单
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 保单查询结果
     */
    PolicyQueryResult findPolicyById(String policyId, String tenantId);

    /**
     * 根据客户ID查询保单
     *
     * @param customerId 客户ID
     * @param tenantId 租户ID
     * @param page 页码
     * @param size 每页大小
     * @return 保单查询结果列表
     */
    List<PolicyQueryResult> findPoliciesByCustomerId(String customerId, String tenantId, int page, int size);

    /**
     * 根据多条件查询保单
     *
     * @param policyNo 保单号
     * @param policyHolderName 投保人姓名
     * @param insuredName 被保险人姓名
     * @param productCode 险种编码
     * @param status 保单状态
     * @param effectiveDateStart 生效日期开始
     * @param effectiveDateEnd 生效日期结束
     * @param expiryDateStart 终止日期开始
     * @param expiryDateEnd 终止日期结束
     * @param tenantId 租户ID
     * @param page 页码
     * @param size 每页大小
     * @return 保单查询结果列表
     */
    List<PolicyQueryResult> findPoliciesByMultipleConditions(String policyNo, String policyHolderName,
                                                             String insuredName, String productCode, String status,
                                                             LocalDateTime effectiveDateStart,
                                                             LocalDateTime effectiveDateEnd,
                                                             LocalDateTime expiryDateStart, LocalDateTime expiryDateEnd,
                                                             String tenantId, int page, int size);

    /**
     * 根据状态查询保单
     *
     * @param status 保单状态
     * @param tenantId 租户ID
     * @param page 页码
     * @param size 每页大小
     * @return 保单查询结果列表
     */
    List<PolicyQueryResult> findPoliciesByStatus(String status, String tenantId, int page, int size);

    /**
     * 根据日期范围查询保单
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param dateType 日期类型：effectiveDate（生效日期）或 expiryDate（终止日期）
     * @param tenantId 租户ID
     * @param page 页码
     * @param size 每页大小
     * @return 保单查询结果列表
     */
    List<PolicyQueryResult> findPoliciesByDateRange(LocalDateTime startDate, LocalDateTime endDate, String dateType,
                                                    String tenantId, int page, int size);
}
