package com.titanium.policy.infrastructure.projection;

import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import com.titanium.policy.infrastructure.entity.ProposalEntity;
import com.titanium.policy.infrastructure.repository.jpa.JpaProposalRepository;
import com.titanium.policy.query.ProposalQuery;

import lombok.RequiredArgsConstructor;

/**
 * 投保意向单投影类，用于处理投保意向单领域事件并更新数据库
 */
@Component
@RequiredArgsConstructor
public class ProposalProjection {

    private final JpaProposalRepository proposalRepository;

    /**
     * 处理投保意向单查询
     */
    @QueryHandler
    public ProposalEntity handle(ProposalQuery query) {
        return proposalRepository.findByProposalNoAndTenantId(query.proposalId(), query.tenantId()).orElse(null);
    }
}
