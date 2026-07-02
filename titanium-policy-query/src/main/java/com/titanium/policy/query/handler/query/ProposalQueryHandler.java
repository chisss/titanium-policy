package com.titanium.policy.query.handler.query;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import com.titanium.policy.query.query.FindProposalByIdQuery;
import com.titanium.policy.query.query.FindProposalByNoQuery;
import com.titanium.policy.query.result.ProposalQueryResult;
import com.titanium.policy.query.service.ProposalQueryService;

import lombok.AllArgsConstructor;

/**
 * 投保意向单查询处理器
 * <p>
 * 处理意向单读模型查询请求，委托 {@link ProposalQueryService} 查询 {@code t_proposal_view}。
 * </p>
 */
@Component
@AllArgsConstructor
@ProcessingGroup("policy-query-group")
public class ProposalQueryHandler {

    private final ProposalQueryService proposalQueryService;

    /**
     * 根据ID查询意向单
     *
     * @param query 查询条件
     * @return 意向单查询结果
     */
    @QueryHandler
    public ProposalQueryResult handle(FindProposalByIdQuery query) {
        return proposalQueryService.findProposalById(query.proposalId(), query.tenantId());
    }

    /**
     * 根据编号查询意向单
     *
     * @param query 查询条件
     * @return 意向单查询结果
     */
    @QueryHandler
    public ProposalQueryResult handle(FindProposalByNoQuery query) {
        return proposalQueryService.findProposalByNo(query.proposalNo(), query.tenantId());
    }
}
