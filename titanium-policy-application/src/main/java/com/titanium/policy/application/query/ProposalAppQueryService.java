package com.titanium.policy.application.query;

import java.util.Optional;

import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import com.titanium.policy.query.query.FindProposalByIdQuery;
import com.titanium.policy.query.query.FindProposalByNoQuery;
import com.titanium.policy.query.result.ProposalQueryResult;

import jakarta.annotation.Resource;

/**
 * 投保意向单查询服务（CQRS 读侧入口）
 * <p>
 * 读写分离落地：经 {@link QueryGateway} 派发查询到读侧 {@code ProposalQueryHandler}，
 * 查询 {@code ProposalView} 读模型，<b>不再回退到写模型聚合 {@code Proposal}</b>。
 * </p>
 */
@Service
public class ProposalAppQueryService {

    @Resource
    private QueryGateway queryGateway;

    /**
     * 根据ID查询意向单（读模型）
     *
     * @param proposalId 意向单ID
     * @param tenantId 租户ID
     * @return 意向单查询结果，不存在时为空
     */
    public Optional<ProposalQueryResult> findById(String proposalId, String tenantId) {
        ProposalQueryResult result = queryGateway.query(new FindProposalByIdQuery(proposalId, tenantId),
                ResponseTypes.instanceOf(ProposalQueryResult.class)).join();
        return Optional.ofNullable(result);
    }

    /**
     * 根据编号查询意向单（读模型）
     *
     * @param proposalNo 意向单编号
     * @param tenantId 租户ID
     * @return 意向单查询结果，不存在时为空
     */
    public Optional<ProposalQueryResult> findByProposalNo(String proposalNo, String tenantId) {
        ProposalQueryResult result = queryGateway.query(new FindProposalByNoQuery(proposalNo, tenantId),
                ResponseTypes.instanceOf(ProposalQueryResult.class)).join();
        return Optional.ofNullable(result);
    }
}
