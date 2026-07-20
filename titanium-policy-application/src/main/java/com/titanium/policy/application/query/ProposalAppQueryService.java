package com.titanium.policy.application.query;

import java.util.List;
import java.util.Optional;

import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import com.titanium.policy.query.query.FindProposalByIdQuery;
import com.titanium.policy.query.query.FindProposalByNoQuery;
import com.titanium.policy.query.query.FindProposalsByConditionQuery;
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

    /**
     * 多条件分页查询意向单列表（读模型）
     *
     * @param proposalNo          意向单编号（模糊，可空）
     * @param expectedProductCode 险种编码（精确，可空）
     * @param status              状态枚举 name（可空）
     * @param tenantId            租户ID
     * @param page                页码（从0开始）
     * @param size                每页条数
     * @return 意向单查询结果列表
     */
    public List<ProposalQueryResult> findByConditions(String proposalNo, String expectedProductCode, String status,
                                                      String tenantId, int page, int size) {
        return queryGateway.query(
                new FindProposalsByConditionQuery(proposalNo, expectedProductCode, status, tenantId, page, size),
                ResponseTypes.multipleInstancesOf(ProposalQueryResult.class)).join();
    }
}
