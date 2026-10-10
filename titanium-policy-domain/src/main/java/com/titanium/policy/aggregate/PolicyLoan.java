package com.titanium.policy.aggregate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.spring.stereotype.Aggregate;

import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.policy.command.DisbursePolicyLoanCommand;
import com.titanium.policy.command.RepayPolicyLoanCommand;
import com.titanium.policy.entity.loan.PolicyLoanEntry;
import com.titanium.policy.entity.loan.PolicyLoanRepaymentRecord;
import com.titanium.policy.event.PolicyLoanDisbursedEvent;
import com.titanium.policy.event.PolicyLoanRepaidEvent;
import com.titanium.policy.exception.PolicyBusinessRuleException;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 保单贷款账户聚合根（g04-03，事件溯源）。
 * <p>
 * 贷款账户是保单合同的质押负债，资金事实须完整审计，故选事件溯源写模型（CLAUDE.md 写模型
 * 选型判据①④）：放款本金与冻结利率一经入账不可变更，历史由事件流完整重放。聚合标识 =
 * {@code loanAccountId = "LOAN:" + policyId}（保单与贷款账户 1:1，经 {@link #loanAccountIdOf}
 * 派生）；每笔放款为一条贷款明细（{@code loanId} 由调用方生成，全局唯一幂等键）；还款（g04-04）
 * 以追加还款记录的方式摊销未偿本金与利息，计息与「先息后本」分配在 {@link #repay} 内现场完成
 * （持状态者计算，调用方不复算）。
 * </p>
 * <p>
 * 🔴 聚合标识空间隔离（g04-03 真机缺陷修复）：贷款账户<b>不得</b>复用保单ID作聚合标识——
 * JPA 事件存储按标识返回全部事件（不按类型过滤），共用标识时 PolicyLoan 回放 Policy 事件流
 * （无处理器静默跳过）后聚合标识仍未落位，抛 IncompatibleAggregateException。故独立派生
 * {@code loanAccountId}，与保单聚合事件流物理隔离。
 * </p>
 * <p>
 * 🔴 创建与追加共用同一命令 {@code DisbursePolicyLoanCommand}：Axon 同一命令类型不得同时
 * 声明构造器命令处理器与实例方法命令处理器（会注册两个同名 handler，CommandBus 重复解析器
 * 只保留其一，另一路径静默失效）。正确机制是 {@link CreationPolicy} 的
 * {@code CREATE_IF_MISSING}——实例方法处理器内部合成 create-or-update 语义：聚合不存在时经
 * 无参构造器创建后执行本方法（首笔建账户），存在时加载后执行（追加放款）。
 * </p>
 */
@Aggregate
@Getter
@NoArgsConstructor
public class PolicyLoan {

    /** 聚合标识：贷款账户ID（"LOAN:" + policyId，与保单聚合标识空间隔离） */
    @AggregateIdentifier
    private String loanAccountId;

    /** 保单ID（业务字段：贷款账户与保单 1:1） */
    private String policyId;

    /** 租户ID */
    private String tenantId;

    /** 贷款明细（每笔放款一条，loanId 唯一） */
    private List<PolicyLoanEntry> entries;

    /**
     * 派生贷款账户聚合标识：{@code "LOAN:" + policyId}。
     * <p>
     * 唯一派生点——命令构造、仓储加载与事件落位均经本方法取标识，禁止调用方自行拼接前缀。
     * </p>
     */
    public static String loanAccountIdOf(String policyId) {
        return "LOAN:" + policyId;
    }

    /**
     * 放款（首笔创建贷款账户 / 已存在时追加明细，CREATE_IF_MISSING 两态共用）。
     * <p>
     * 首笔时聚合刚经无参构造器创建，{@code policyId} 尚未设置（事件回放后由
     * {@link #on(PolicyLoanDisbursedEvent)} 落位），此时无既有租户可比对，跳过租户守护
     * （与创建型构造器命令同豁免，见 CLAUDE.md 跨租户守护铁律）；账户已存在时必须同租户。
     * </p>
     * <p>
     * 🔴 幂等重试语义（g04-03 保全放款三连写重试安全的关键）：{@code loanId} 由调用方稳定生成，
     * 同键同额的重试是<b>同一笔放款</b>——不产生新事件，直接返回既有明细（利率与放款日期以首次
     * 入账为准，故重复请求即使携带新利率/新日期也以既有明细回显）；同键<b>异额</b>则是调用方串号，
     * 仍显式拒绝。返回值恒为该笔放款明细，供契约层回显首次事实（重试载荷不得漂移）。
     * </p>
     */
    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
    public PolicyLoanEntry disburse(DisbursePolicyLoanCommand command) {
        boolean accountExists = this.loanAccountId != null;
        if (accountExists) {
            requireSameTenant(command.tenantId());
            PolicyLoanEntry existing = findEntry(command.loanId());
            if (existing != null) {
                if (existing.loanAmount().compareTo(command.loanAmount()) != 0) {
                    throw new PolicyBusinessRuleException(PolicyErrorCode.POLICY_RULE_VIOLATION,
                            "贷款明细业务号重复放款且金额不一致: " + command.loanId());
                }
                return existing;
            }
        }
        validateDisburse(command);
        PolicyLoanEntry entry = new PolicyLoanEntry(
                command.loanId(), command.loanAmount(), command.interestRate(), command.loanDate(), List.of());
        applyDisbursed(command);
        return entry;
    }

    /** 按贷款明细业务号查已有明细（幂等重试判定）；未命中返回 {@code null}。 */
    private PolicyLoanEntry findEntry(String loanId) {
        if (entries == null) {
            return null;
        }
        return entries.stream().filter(entry -> entry.loanId().equals(loanId)).findFirst().orElse(null);
    }

    /** 事件溯源回放：放款明细按事件顺序追加（重放幂等） */
    @EventSourcingHandler
    public void on(PolicyLoanDisbursedEvent event) {
        this.loanAccountId = event.loanAccountId();
        this.policyId = event.policyId();
        this.tenantId = event.tenantId();
        if (this.entries == null) {
            this.entries = new ArrayList<>();
        }
        this.entries.add(new PolicyLoanEntry(
                event.loanId(), event.loanAmount(), event.interestRate(), event.loanDate(), List.of()));
    }

    /**
     * 未结清贷款明细（已偿清除外），供取数契约经事件流强一致重建后读取。
     *
     * @return 未结清明细列表（无则空列表，绝不返回 null）
     */
    public List<PolicyLoanEntry> outstandingEntries() {
        return entries.stream().filter(entry -> !entry.settled()).toList();
    }

    /**
     * 贷款还款（部分还款 / 提前清偿 / 结清共用，g04-04）。
     * <p>
     * 🔴 <b>分配与计息在聚合内现场完成</b>：金额拆分依赖未偿状态与计息区间，只有持有全量还款记录的
     * 聚合才能算出唯一正确的答案（调用方复算必然漂移）。规则为「先息后本 + 单利 365」：
     * </p>
     * <ol>
     *   <li>段增量利息 = 未偿本金 × 冻结利率 × (上次计息日 → 还款日自然日) / 365（scale 2 HALF_UP）；
     *       首个区间（放款日 → 首次还款日）与保全域 {@code PolicyLoanDetail.accruedInterest(days)}
     *       逐位一致（g04-04 / AC-05）；</li>
     *   <li>欠款总额 = 未偿本金 + 已计未付利息（含本段增量），还款额超过即显式拒绝
     *       （AC-05 反向判据，不静默截断）；</li>
     *   <li>先冲利息（至多冲尽已计未付利息），余额冲本金；</li>
     *   <li>本金归零即结清（{@code settled}），事件携带结清标记供保全域触发合同要素回写与留痕。</li>
     * </ol>
     * <p>
     * 🔴 幂等重试语义：{@code repaymentId} 由调用方稳定生成，同键同额的重试是<b>同一笔还款</b>——
     * 不产生新事件，直接返回既有明细（分配与日期以首次入账为准）；同键<b>异额</b>则是调用方串号，
     * 仍显式拒绝。
     * </p>
     *
     * @param command 还款命令（还款额与还款日期由保全受理事实承载）
     * @return 还款后明细（含本次分配结果与余额，供契约层回显）
     */
    @CommandHandler
    public PolicyLoanEntry repay(RepayPolicyLoanCommand command) {
        requireSameTenant(command.tenantId());
        PolicyLoanEntry entry = findEntry(command.loanId());
        if (entry == null) {
            throw new PolicyBusinessRuleException(PolicyErrorCode.POLICY_RULE_VIOLATION,
                    "贷款明细不存在: " + command.loanId());
        }
        PolicyLoanRepaymentRecord existing = entry.findRepayment(command.repaymentId());
        if (existing != null) {
            if (existing.repaymentAmount().compareTo(command.repaymentAmount()) != 0) {
                throw new PolicyBusinessRuleException(PolicyErrorCode.POLICY_RULE_VIOLATION,
                        "还款业务号重复且金额不一致: " + command.repaymentId());
            }
            return entry;
        }
        validateRepay(command, entry);

        BigDecimal accruedInterest = entry.accruedInterestTo(command.repaymentDate());
        BigDecimal unpaidInterest = entry.unpaidInterest().add(accruedInterest);
        BigDecimal outstandingPrincipal = entry.outstandingPrincipal();
        BigDecimal totalOutstanding = outstandingPrincipal.add(unpaidInterest);
        if (command.repaymentAmount().compareTo(totalOutstanding) > 0) {
            throw new PolicyBusinessRuleException(PolicyErrorCode.POLICY_RULE_VIOLATION,
                    "还款额超过欠款总额: 还款额 " + command.repaymentAmount() + " > 欠款总额 " + totalOutstanding);
        }
        // 先息后本：先冲已计未付利息（至多冲尽），余额冲本金
        BigDecimal interestPortion = command.repaymentAmount().min(unpaidInterest);
        BigDecimal principalPortion = command.repaymentAmount().subtract(interestPortion);
        BigDecimal outstandingPrincipalAfter = outstandingPrincipal.subtract(principalPortion);
        BigDecimal outstandingInterestAfter = unpaidInterest.subtract(interestPortion);
        boolean settled = outstandingPrincipalAfter.signum() == 0;

        PolicyLoanRepaymentRecord record = new PolicyLoanRepaymentRecord(
                command.repaymentId(), command.repaymentAmount(), interestPortion, principalPortion,
                accruedInterest, command.repaymentDate());
        AggregateLifecycle.apply(new PolicyLoanRepaidEvent(
                command.loanAccountId(), command.policyId(), command.loanId(), command.repaymentId(),
                command.repaymentAmount(), interestPortion, principalPortion, accruedInterest,
                outstandingPrincipalAfter, outstandingInterestAfter, settled, command.repaymentDate(),
                command.operatorId(), command.tenantId()));
        return entry.withRepayment(record);
    }

    /** 事件溯源回放：还款记录追加到对应明细（重放幂等；追加顺序即事件顺序，用作分段计息依据） */
    @EventSourcingHandler
    public void on(PolicyLoanRepaidEvent event) {
        PolicyLoanRepaymentRecord record = new PolicyLoanRepaymentRecord(
                event.repaymentId(), event.repaymentAmount(), event.interestPortion(),
                event.principalPortion(), event.accruedInterest(), event.repaymentDate());
        for (int index = 0; index < entries.size(); index++) {
            if (entries.get(index).loanId().equals(event.loanId())) {
                entries.set(index, entries.get(index).withRepayment(record));
                return;
            }
        }
        // 事件来自本聚合事件流，loanId 必然命中；未命中等价于事件流被外部篡改，须显式暴露
        throw new IllegalStateException("还款事件未匹配到贷款明细: " + event.loanId());
    }

    /** 还款校验：还款额为正、还款日期非空且不早于上次计息日（重复键判定已上提至 {@link #repay}）。 */
    private void validateRepay(RepayPolicyLoanCommand command, PolicyLoanEntry entry) {
        if (command.repaymentAmount() == null || command.repaymentAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PolicyBusinessRuleException(PolicyErrorCode.POLICY_RULE_VIOLATION,
                    "还款金额必须为正数: " + command.repaymentAmount());
        }
        if (command.repaymentDate() == null) {
            throw new PolicyBusinessRuleException(PolicyErrorCode.POLICY_RULE_VIOLATION, "还款日期缺失");
        }
        if (command.repaymentDate().toLocalDate().isBefore(entry.lastAccrualDate().toLocalDate())) {
            throw new PolicyBusinessRuleException(PolicyErrorCode.POLICY_RULE_VIOLATION,
                    "还款日期不得早于上次计息日: " + entry.lastAccrualDate());
        }
    }

    /** 放款校验：本金为正、利率落 [0,1]、放款时点非空（重复键判定已上提至 {@link #disburse}）。 */
    private void validateDisburse(DisbursePolicyLoanCommand command) {
        if (command.loanAmount() == null || command.loanAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PolicyBusinessRuleException(PolicyErrorCode.POLICY_RULE_VIOLATION,
                    "放款金额必须为正数: " + command.loanAmount());
        }
        if (command.interestRate() == null || command.interestRate().compareTo(BigDecimal.ZERO) < 0
                || command.interestRate().compareTo(BigDecimal.ONE) > 0) {
            throw new PolicyBusinessRuleException(PolicyErrorCode.POLICY_RULE_VIOLATION,
                    "冻结利率必须落在 [0,1]: " + command.interestRate());
        }
        if (command.loanDate() == null) {
            throw new PolicyBusinessRuleException(PolicyErrorCode.POLICY_RULE_VIOLATION,
                    "放款日期缺失");
        }
    }

    private void applyDisbursed(DisbursePolicyLoanCommand command) {
        AggregateLifecycle.apply(new PolicyLoanDisbursedEvent(
                command.loanAccountId(), command.loanId(), command.policyId(), command.loanAmount(),
                command.interestRate(), command.loanDate(), command.operatorId(), command.tenantId()));
    }

    /** 同租户守护（跨租户写入防护，铁律：每个命令处理器首条语句调用；首笔创建豁免） */
    private void requireSameTenant(String commandTenantId) {
        if (commandTenantId == null || commandTenantId.isBlank()
                || !Objects.equals(this.tenantId, commandTenantId)) {
            throw new PolicyBusinessRuleException(PolicyErrorCode.POLICY_NOT_EXIST, "保单贷款账户不存在");
        }
    }
}
