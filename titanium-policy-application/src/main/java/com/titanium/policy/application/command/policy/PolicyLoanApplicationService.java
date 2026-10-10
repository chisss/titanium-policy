package com.titanium.policy.application.command.policy;

import java.util.Objects;
import java.util.function.Function;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.axonframework.modelling.command.Repository;
import org.springframework.stereotype.Service;

import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.exception.DomainException;
import com.titanium.policy.aggregate.Policy;
import com.titanium.policy.command.DisbursePolicyLoanCommand;
import com.titanium.policy.command.RepayPolicyLoanCommand;
import com.titanium.policy.common.enums.PolicyStatusCode;
import com.titanium.policy.entity.loan.PolicyLoanEntry;

import lombok.RequiredArgsConstructor;

/**
 * 保单贷款写侧应用门面（application/command，g04-03）。
 * <p>
 * 放款入口：校验保单存在、租户相符、保单生效后派发 {@link DisbursePolicyLoanCommand} 至贷款
 * 账户聚合。业务规则（金额/利率值域、贷款明细幂等）内聚在 {@code PolicyLoan} 聚合根，本类
 * 只做「保单状态前置校验 + 命令派发」编排，不写业务判断。
 * </p>
 * <p>
 * 🔴 失败关闭：保单不存在或租户不符 → {@link PolicyErrorCode#POLICY_NOT_EXIST}（不泄漏资源
 * 存在性）；保单未生效 → {@link PolicyErrorCode#POLICY_NOT_EFFECTIVE}。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class PolicyLoanApplicationService {

    private final CommandGateway commandGateway;
    private final Repository<Policy> policyRepository;

    /**
     * 放款：保单状态前置校验后派发放款命令（同步等返回）。
     *
     * @param command 放款命令（贷款明细业务号由调用方生成，幂等键）
     * @return 该笔放款明细（幂等重试时返回首次入账明细，利率与放款日期以首次为准）
     */
    public PolicyLoanEntry disburse(DisbursePolicyLoanCommand command) {
        Policy policy = loadPolicy(command.tenantId(), command.policyId());
        if (policy.getStatus() == null || policy.getStatus().statusCode() != PolicyStatusCode.EFFECTIVE) {
            throw new DomainException(PolicyErrorCode.POLICY_NOT_EFFECTIVE,
                    "保单未生效，不可贷款: " + command.policyId());
        }
        // 🔴 服务受限是放款的准入闸门（g04-09）：贷款是「新增负债」，与状态闸门同一性质。
        //    判据取自 loadPolicy 强一致装载的聚合（非读模型，投影滞后会失败开放）；
        //    服务受限与保单状态正交——已挂失保单仍是 EFFECTIVE，故必须单列这道闸门。
        if (policy.serviceRestricted()) {
            throw new DomainException(PolicyErrorCode.POLICY_SERVICE_RESTRICTED,
                    "保单处于服务受限状态 " + policy.serviceRestrictions().activeTypes()
                            + "，不可贷款: " + command.policyId());
        }
        return commandGateway.sendAndWait(command);
    }

    /**
     * 还款：保单存在性与租户校验后派发还款命令（同步等返回）。
     * <p>
     * 🔴 还款<b>不设保单状态门槛</b>——债务不因保单状态变化而消灭，状态闸门只把守新增负债
     * （{@link #disburse}）；仅校验保单存在且租户相符。
     * </p>
     *
     * @param command 还款命令（还款业务号由调用方生成，幂等键；金额拆分由聚合现场计算）
     * @return 还款后明细（幂等重试时该还款记录以首次入账为准）
     */
    public PolicyLoanEntry repay(RepayPolicyLoanCommand command) {
        loadPolicy(command.tenantId(), command.policyId());
        return commandGateway.sendAndWait(command);
    }

    /**
     * 经事件流重建保单聚合并做租户校验（写侧强一致，同
     * {@code PolicyFinancialSnapshotQueryAppService} 口径）。
     */
    private Policy loadPolicy(String tenantId, String policyId) {
        // ES 仓储 load 要求线程内有 UnitOfWork：本方法先于命令派发执行，线程尚无命令上下文，
        // 须手工开启，提取聚合根后回滚（只读用途，不产生事件副作用，同时释放聚合锁）。
        UnitOfWork<?> unitOfWork = DefaultUnitOfWork.startAndGet(null);
        try {
            // load 返回锁感知包装，经 invoke 提取聚合根（只读用途，不产生命令副作用）
            Policy policy = policyRepository.load(policyId).invoke(Function.identity());
            if (!Objects.equals(tenantId, policy.getTenantId())) {
                throw new DomainException(PolicyErrorCode.POLICY_NOT_EXIST, "保单不存在: " + policyId);
            }
            return policy;
        } catch (AggregateNotFoundException exception) {
            throw new DomainException(PolicyErrorCode.POLICY_NOT_EXIST, "保单不存在: " + policyId);
        } finally {
            unitOfWork.rollback();
        }
    }
}
