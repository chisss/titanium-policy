package com.titanium.policy.query.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.titanium.policy.query.view.InsuranceView;
import com.titanium.policy.valueobject.insurance.InsuranceStatus;

/**
 * 投保单读模型仓储
 * <p>
 * CQRS 查询侧仓储，直接访问读模型表 {@code t_insurance_view}，与写侧仓储隔离。 所有查询方法强制携带
 * {@code tenantId} 实现多租户数据隔离。 继承 {@link JpaSpecificationExecutor} 以支持多条件动态组合查询。
 * </p>
 */
@Repository
public interface InsuranceViewRepository
        extends JpaRepository<InsuranceView, String>, JpaSpecificationExecutor<InsuranceView> {

    Optional<InsuranceView> findByInsuranceIdAndTenantId(String insuranceId, String tenantId);

    Optional<InsuranceView> findByInsuranceNoAndTenantId(String insuranceNo, String tenantId);

    Optional<InsuranceView> findByProposalIdAndTenantId(String proposalId, String tenantId);

    List<InsuranceView> findByHolderIdAndTenantId(String holderId, String tenantId, Pageable pageable);

    List<InsuranceView> findByStatusAndTenantId(InsuranceStatus.StatusCode status, String tenantId, Pageable pageable);
}
