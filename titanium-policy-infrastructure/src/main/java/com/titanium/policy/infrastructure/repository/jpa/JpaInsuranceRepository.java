package com.titanium.policy.infrastructure.repository.jpa;

import com.titanium.policy.infrastructure.entity.InsuranceEntity;
import com.titanium.policy.valueobject.insurance.InsuranceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 投保单JPA仓库接口
 * <p>
 * 用于访问和操作t_insurance_application表中的数据
 * </p>
 */
public interface JpaInsuranceRepository extends JpaRepository<InsuranceEntity, String> {
    /**
     * 根据投保单编号查询投保单
     *
     * @param insuranceNo 投保单编号
     * @param tenantId      租户ID
     * @return 投保单实体
     */
    Optional<InsuranceEntity> findByInsuranceNoAndTenantId(String insuranceNo, String tenantId);



    /**
     * 根据关联意向单ID查询投保单
     *
     * @param proposalId 意向单ID
     * @param tenantId   租户ID
     * @return 投保单实体
     */
    Optional<InsuranceEntity> findByProposalIdAndTenantId(String proposalId, String tenantId);

    /**
     * 根据状态编码查询投保单
     *
     * @param statusCode 状态编码
     * @param tenantId   租户ID
     * @return 投保单实体列表
     */
    Iterable<InsuranceEntity> findByStatusCodeAndTenantId(InsuranceStatus.StatusCode statusCode, String tenantId);

    /**
     * 根据投保人ID查询投保单
     *
     * @param applicantId 投保人ID
     * @param tenantId    租户ID
     * @return 投保单实体列表
     */
    Iterable<InsuranceEntity> findByHolderIdAndTenantId(String applicantId, String tenantId);
}