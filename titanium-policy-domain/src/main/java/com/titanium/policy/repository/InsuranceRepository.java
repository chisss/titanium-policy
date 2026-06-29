package com.titanium.policy.repository;

import java.util.Optional;

import com.titanium.policy.aggregate.Insurance;
import com.titanium.policy.valueobject.insurance.InsuranceStatus;

/**
 * 投保单仓库接口
 * <p>
 * 定义投保单聚合根的存储和检索方法
 * </p>
 */
public interface InsuranceRepository {
    /**
     * 根据ID查询投保单
     *
     * @param insuranceId 投保单ID
     * @param tenantId      租户ID
     * @return 投保单聚合根
     */
    Optional<Insurance> findById(String insuranceId, String tenantId);

    /**
     * 保存投保单
     *
     * @param insurance 投保单聚合根
     * @return 保存后的投保单聚合根
     */
    Insurance save(Insurance insurance);

    /**
     * 删除投保单
     *
     * @param applicationId 投保单ID
     * @param tenantId      租户ID
     */
    void deleteById(String applicationId, String tenantId);

    /**
     * 根据状态查询投保单
     *
     * @param tenantId   租户ID
     * @param statusCode 状态编码
     * @return 投保单迭代器
     */
    Iterable<Insurance> findByStatus(String tenantId, InsuranceStatus.StatusCode statusCode);

    /**
     * 根据投保单编号查询投保单
     *
     * @param insuranceNo 投保单编号
     * @param tenantId      租户ID
     * @return 投保单聚合根
     */
    Optional<Insurance> findByInsuranceNo(String insuranceNo, String tenantId);

    /**
     * 根据关联意向单ID查询投保单
     *
     * @param proposalId 意向单ID
     * @param tenantId   租户ID
     * @return 投保单聚合根
     */
    Optional<Insurance> findByProposalId(String proposalId, String tenantId);

    /**
     * 根据投保人ID查询投保单
     *
     * @param holderId 投保人ID
     * @param tenantId    租户ID
     * @return 投保单迭代器
     */
    Iterable<Insurance> findByApplicantId(String holderId, String tenantId);
}
