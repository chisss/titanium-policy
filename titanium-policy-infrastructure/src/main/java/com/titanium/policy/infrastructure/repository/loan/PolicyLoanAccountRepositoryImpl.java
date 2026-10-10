package com.titanium.policy.infrastructure.repository.loan;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.axonframework.modelling.command.Repository;
import org.springframework.stereotype.Component;

import com.titanium.policy.aggregate.PolicyLoan;
import com.titanium.policy.repository.PolicyLoanAccountRepository;
import com.titanium.policy.valueobject.loan.PolicyLoanAccount;

import lombok.RequiredArgsConstructor;

/**
 * 保单贷款账户仓储实现（g04-02 定义，g04-03 实现升级为事件流重建）。
 * <p>
 * {@link PolicyLoanAccountRepository} 的基础设施实现：经 Axon {@link Repository} 按保单ID重建
 * {@link PolicyLoan} 事件溯源聚合（<b>写侧强一致</b>，不读最终一致读模型），提取未结清明细后转
 * 领域值对象。投影延迟竞态对取数契约不可见——刚放款的明细立即参与未偿本息推导。
 * </p>
 * <p>
 * 🔴 无贷款账户（从未放款）→ 空列表（业务事实，非异常）；租户不符视同不存在（调用方在
 * 保单聚合重建环节已做同口径校验，此处不泄漏资源存在性）。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class PolicyLoanAccountRepositoryImpl implements PolicyLoanAccountRepository {

    private final Repository<PolicyLoan> policyLoanRepository;

    @Override
    public List<PolicyLoanAccount> findOutstanding(String policyId, String tenantId) {
        // ES 仓储 load 要求线程内有 UnitOfWork：查询路径无命令上下文，须手工开启，提取聚合根后
        // 回滚（只读用途，不产生事件副作用，同时释放聚合锁）。
        UnitOfWork<?> unitOfWork = DefaultUnitOfWork.startAndGet(null);
        try {
            // load 返回锁感知包装，经 invoke 提取聚合根（只读用途，不产生命令副作用）；
            // 聚合标识 = loanAccountId（与保单聚合标识空间隔离），由保单ID派生
            PolicyLoan account = policyLoanRepository.load(PolicyLoan.loanAccountIdOf(policyId))
                    .invoke(Function.identity());
            if (!Objects.equals(tenantId, account.getTenantId())) {
                return List.of();
            }
            // 未偿本息一律取聚合派生值（g04-04）：计息基数已随部分还款冲减，且含已计提区间——
            // 调用方不得按放款日自行计息
            LocalDateTime valuationTime = LocalDateTime.now();
            return account.outstandingEntries().stream()
                    .map(entry -> new PolicyLoanAccount(
                            entry.outstandingPrincipal(), entry.outstandingInterestAt(valuationTime)))
                    .toList();
        } catch (AggregateNotFoundException exception) {
            // 从未放款：无贷款账户即无未结贷款（业务事实，非异常）
            return List.of();
        } finally {
            unitOfWork.rollback();
        }
    }
}
