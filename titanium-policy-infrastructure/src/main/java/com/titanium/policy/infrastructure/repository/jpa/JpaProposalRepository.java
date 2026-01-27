package com.titanium.policy.infrastructure.repository.jpa;

import com.titanium.policy.infrastructure.entity.ProposalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 投保意向单JPA仓库接口
 * <p>
 * 用于访问和操作t_proposal表中的数据
 * </p>
 */
public interface JpaProposalRepository extends JpaRepository<ProposalEntity, String> {
    /**
     * 根据意向单编号查询意向单
     *
     * @param proposalNo 意向单编号
     * @param tenantId   租户ID
     * @return 投保意向单实体
     */
    Optional<ProposalEntity> findByProposalNoAndTenantId(String proposalNo, String tenantId);

    /**
     * 根据状态编码查询意向单
     *
     * @param statusCode 状态编码
     * @param tenantId   租户ID
     * @return 投保意向单实体列表
     */
    Iterable<ProposalEntity> findByStatusCodeAndTenantId(String statusCode, String tenantId);

    /**
     * 根据客户ID查询意向单
     *
     * @param customerId 客户ID
     * @param tenantId   租户ID
     * @return 投保意向单实体列表
     */
    Iterable<ProposalEntity> findByCustomerIdAndTenantId(String customerId, String tenantId);
}