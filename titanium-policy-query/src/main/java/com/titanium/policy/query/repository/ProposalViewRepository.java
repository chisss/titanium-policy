package com.titanium.policy.query.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.titanium.policy.common.enums.ProposalStatusCode;
import com.titanium.policy.query.view.ProposalView;

/**
 * 投保意向单读模型仓储
 * <p>
 * CQRS 查询侧仓储，直接访问读模型表 {@code t_proposal_view}，与写侧仓储隔离。 所有查询方法强制携带
 * {@code tenantId} 实现多租户数据隔离。 继承 {@link JpaSpecificationExecutor} 以支持多条件动态组合查询。
 * </p>
 */
@Repository
public interface ProposalViewRepository
        extends JpaRepository<ProposalView, String>, JpaSpecificationExecutor<ProposalView> {

    Optional<ProposalView> findByProposalIdAndTenantId(String proposalId, String tenantId);

    Optional<ProposalView> findByProposalNoAndTenantId(String proposalNo, String tenantId);

    List<ProposalView> findByCustomerIdAndTenantId(String customerId, String tenantId, Pageable pageable);

    List<ProposalView> findByStatusAndTenantId(ProposalStatusCode status, String tenantId, Pageable pageable);
}
