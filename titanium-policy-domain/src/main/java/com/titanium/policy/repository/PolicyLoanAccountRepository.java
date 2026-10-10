package com.titanium.policy.repository;

import java.util.List;

import com.titanium.policy.valueobject.loan.PolicyLoanAccount;

/**
 * 保单贷款账户仓储端口（domain/repository，g04-02 定义，g04-03 实现升级为事件流重建）。
 * <p>
 * 贷款账户为<b>事件溯源</b>写模型（{@code PolicyLoan} 聚合），本端口表达「查保单名下未结清
 * 贷款」的领域能力，由基础设施层 {@code PolicyLoanAccountRepositoryImpl} 实现——经 Axon
 * {@code Repository<PolicyLoan>} 按保单ID重建聚合（写侧强一致，不读最终一致读模型），再
 * 提取未结明细转领域值对象。
 * </p>
 * <p>
 * 与读模型仓储命名分立不撞名：读侧是 {@code XxxViewRepository}（query 层），本端口是写侧
 * 事件溯源聚合的取数端口（domain 定义、infrastructure 实现）。
 * </p>
 */
public interface PolicyLoanAccountRepository {

    /**
     * 查保单名下全部未结清贷款（按租户隔离）。
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 未结清贷款列表（无则空列表，绝不返回 null）
     */
    List<PolicyLoanAccount> findOutstanding(String policyId, String tenantId);
}
