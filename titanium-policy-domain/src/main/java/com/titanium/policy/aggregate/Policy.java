package com.titanium.policy.aggregate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import com.titanium.common.domain.BaseAggregate;
import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.command.ActivatePolicyCommand;
import com.titanium.policy.command.AddInsuredMemberCommand;
import com.titanium.policy.command.ApplyPolicyEndorsementCommand;
import com.titanium.policy.command.CancelPolicyCommand;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.command.CreatePolicyDirectlyCommand;
import com.titanium.policy.command.DistributeDividendCommand;
import com.titanium.policy.command.IssuePolicyCommand;
import com.titanium.policy.command.LapsePolicyCommand;
import com.titanium.policy.command.LinkInvestmentAccountCommand;
import com.titanium.policy.command.LinkSubPolicyCommand;
import com.titanium.policy.command.MatureDuePolicyCommand;
import com.titanium.policy.command.MaturePolicyCommand;
import com.titanium.policy.command.PayAnnuityBenefitCommand;
import com.titanium.policy.command.ReinstatePolicyCommand;
import com.titanium.policy.command.RemoveInsuredMemberCommand;
import com.titanium.policy.command.ResumePolicyCommand;
import com.titanium.policy.command.StartAnnuityPayoutCommand;
import com.titanium.policy.command.SuspendPolicyCommand;
import com.titanium.policy.command.TerminatePolicyCommand;
import com.titanium.policy.command.UpdateAccountValueCommand;
import com.titanium.policy.command.WaivePremiumCommand;
import com.titanium.policy.common.constant.PolicyConstants;
import com.titanium.policy.common.enums.PremiumWaiverReason;
import com.titanium.policy.entity.Endorsement;
import com.titanium.policy.entity.InsuranceProduct;
import com.titanium.policy.entity.PaymentRecord;
import com.titanium.policy.entity.Subject;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.event.AccountValueUpdatedEvent;
import com.titanium.policy.event.AnnuityBenefitPaidEvent;
import com.titanium.policy.event.AnnuityPayoutStartedEvent;
import com.titanium.policy.event.DividendDistributedEvent;
import com.titanium.policy.event.InsuredMemberAddedEvent;
import com.titanium.policy.event.InsuredMemberRemovedEvent;
import com.titanium.policy.event.InvestmentAccountLinkedEvent;
import com.titanium.policy.event.PolicyActivatedEvent;
import com.titanium.policy.event.PolicyCancelledEvent;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.event.PolicyEndorsedEvent;
import com.titanium.policy.event.PolicyExpiredEvent;
import com.titanium.policy.event.PolicyIssuedEvent;
import com.titanium.policy.event.PolicyLapsedEvent;
import com.titanium.policy.event.PolicyMaturedEvent;
import com.titanium.policy.event.PolicyPaymentRecordedEvent;
import com.titanium.policy.event.PolicyReinstatedEvent;
import com.titanium.policy.event.PolicyResumedEvent;
import com.titanium.policy.event.PolicySuspendedEvent;
import com.titanium.policy.event.PolicyTerminatedEvent;
import com.titanium.policy.event.PremiumWaivedEvent;
import com.titanium.policy.event.SubPolicyLinkedEvent;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.valueobject.AnnuityPayoutPlan;
import com.titanium.policy.valueobject.DeductibleRule;
import com.titanium.policy.valueobject.PolicyBasicInfo;
import com.titanium.policy.valueobject.PolicyDocument;
import com.titanium.policy.valueobject.PolicyNo;
import com.titanium.policy.valueobject.PolicyRelation;
import com.titanium.policy.valueobject.PolicyStatus;
import com.titanium.policy.valueobject.PremiumPlan;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * 正式保单聚合根
 * <p>
 * 保单域核心聚合根，管理正式保单全生命周期：创建 → 签发 → 生效 → 暂停/恢复/终止/到期。 保单域是"执行者"，被动接收保全域的状态变更指令。
 * </p>
 * <p>
 * 继承 {@link BaseAggregate}，复用租户ID、创建时间、更新时间等领域审计字段。
 * </p>
 */
@Aggregate
@Getter
@SuperBuilder(toBuilder = true)
public class Policy extends BaseAggregate {
    /** 聚合根唯一标识 */
    @AggregateIdentifier
    private String                 policyId;
    /** 保单号，对外唯一 */
    private PolicyNo               policyNo;
    /** 关联投保单ID */
    private String                 insuranceId;
    /** 保单形态 */
    private PolicyForm             policyForm;
    /** 父保单ID */
    private String                 parentPolicyId;
    /** 签发机构 */
    private String                 issueOrg;
    /** 签发时间 */
    private LocalDateTime          issueTime;
    /** 保单基本信息 */
    private PolicyBasicInfo        basicInfo;
    /** 保单关系 */
    private PolicyRelation         policyRelation;
    /** 投保险种列表 */
    private List<InsuranceProduct> insuranceProducts;
    /** 保险标的列表 */
    private List<Subject>          subjects;
    /** 保费计划 */
    private PremiumPlan            premiumPlan;
    /** 免赔规则列表 */
    private List<DeductibleRule>   deductibleRules;
    /** 投保参与方清单 */
    private InsuredPartyList       insuredPartyList;
    /** 关联投资账户ID（投连/万能保单出单后挂接，investment 域生成） */
    private String                 investmentAccountId;
    /** 投资账户最新价值（投连/万能保单，由 investment 域回写，展示型最终一致数据） */
    private java.math.BigDecimal   investmentAccountValue;
    /** 产品ID（承保载体，供签发事件透传下游监管采集/自动分保） */
    private String                 productId;
    /** 保额（承保关键要素，供签发事件透传下游） */
    private Money                  sumInsured;
    /** 缴费记录列表 */
    private List<PaymentRecord>    paymentRecords;
    /** 保单单证列表 */
    private List<PolicyDocument>   policyDocuments;
    /** 批单列表（数据/要素类批改留痕） */
    private List<Endorsement>      endorsements;
    /** 保单状态 */
    private PolicyStatus           status;
    /** 险种三级分类（自投保单链路落地，可空以兼容存量事件） */
    private InsuranceProductType   insuranceType;
    /** 年金给付计划（仅年金险进入给付期后存在，非年金险为 null） */
    private AnnuityPayoutPlan      annuityPayoutPlan;
    /** 保费是否已豁免（豁免后投保人无需再缴费，保单持续有效） */
    private boolean                premiumWaived;
    /** 保费豁免原因（未豁免为 null） */
    private PremiumWaiverReason    premiumWaiverReason;
    /** 累计红利（分红险留存类领取方式累积，非分红险/现金领取为 null） */
    private Money                  accumulatedDividend;

    // ==================== CommandHandler ====================

    /**
     * 创建保单（从投保单+核保结果创建）
     */
    @CommandHandler
    public Policy(CreatePolicyCommand command) {
        AggregateLifecycle
                .apply(new PolicyCreatedEvent(command.policyId(), new PolicyNo(command.policyNo()),
                        command.policyForm(), command.productId(), command.startDate(),
                        command.endDate(), command.premium(), command.sumInsured(),
                        new PolicyStatus(PolicyStatus.StatusCode.NOT_EFFECTIVE,
                                LocalDateTime.now(), "创建保单", PolicyConstants.POLICY_SYSTEM),
                        new ArrayList<>(), command.insuranceType(), command.tenantId()));
    }

    /**
     * 一步出单直接创建保单
     */
    @CommandHandler
    public Policy(CreatePolicyDirectlyCommand command) {
        AggregateLifecycle.apply(new PolicyCreatedEvent(command.policyId(), new PolicyNo(command.policyNo()),
                command.policyForm(), command.productId(), command.insurancePeriodStart(),
                command.insurancePeriodEnd(), command.totalPremium(), command.sumInsured(),
                new PolicyStatus(PolicyStatus.StatusCode.NOT_EFFECTIVE, LocalDateTime.now(), "一步出单创建保单",
                        PolicyConstants.POLICY_SYSTEM),
                new ArrayList<>(), command.insuranceType(), command.tenantId()));
    }

    /**
     * 签发保单
     */
    @CommandHandler
    public void handle(IssuePolicyCommand command) {
        if (this.status.statusCode() != PolicyStatus.StatusCode.NOT_EFFECTIVE) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "Only NOT_EFFECTIVE policies can be issued");
        }
        generateDocument();
        Money issuedPremium = this.basicInfo != null ? this.basicInfo.totalPremium() : null;
        AggregateLifecycle.apply(new PolicyIssuedEvent(this.policyId, this.policyNo.value(), this.productId,
                issuedPremium, this.sumInsured, LocalDateTime.now(), command.operatorId(), this.tenantId));
    }

    /**
     * 激活保单（生效）
     */
    @CommandHandler
    public void handle(ActivatePolicyCommand command) {
        if (this.status.statusCode() != PolicyStatus.StatusCode.NOT_EFFECTIVE) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION",
                    "Only NOT_EFFECTIVE policies can be activated");
        }
        // 校验首期保费是否已缴纳
        if (this.premiumPlan != null && this.premiumPlan.paymentStatus() == PremiumPlan.PaymentStatus.UNPAID) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION",
                    "First premium must be paid before activation");
        }
        // 校验保障起期是否到达
        if (this.basicInfo != null && this.basicInfo.insurancePeriodStart() != null
                && this.basicInfo.insurancePeriodStart().isAfter(LocalDateTime.now())) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "Insurance period has not started yet");
        }
        AggregateLifecycle.apply(new PolicyActivatedEvent(this.policyId, LocalDateTime.now(), this.tenantId));
    }

    /**
     * 暂停保单（保全域触发）
     */
    @CommandHandler
    public void handle(SuspendPolicyCommand command) {
        if (this.status.statusCode() != PolicyStatus.StatusCode.EFFECTIVE) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "Only EFFECTIVE policies can be suspended");
        }
        AggregateLifecycle.apply(new PolicySuspendedEvent(this.policyId, LocalDateTime.now(), this.tenantId));
    }

    /**
     * 恢复保单（保全域触发）
     */
    @CommandHandler
    public void handle(ResumePolicyCommand command) {
        if (this.status.statusCode() != PolicyStatus.StatusCode.SUSPENDED) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "Only SUSPENDED policies can be resumed");
        }
        AggregateLifecycle.apply(new PolicyResumedEvent(this.policyId, LocalDateTime.now(), this.tenantId));
    }

    /**
     * 保单失效（宽限期满未缴费，计费/定时任务触发）
     */
    @CommandHandler
    public void handle(LapsePolicyCommand command) {
        if (this.status.statusCode() != PolicyStatus.StatusCode.EFFECTIVE) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "Only EFFECTIVE policies can lapse");
        }
        AggregateLifecycle.apply(new PolicyLapsedEvent(this.policyId, command.reason(), LocalDateTime.now(),
                command.operatorId(), this.tenantId));
    }

    /**
     * 保单复效（保全域触发，失效保单补缴保费+重新核保通过后恢复）
     */
    @CommandHandler
    public void handle(ReinstatePolicyCommand command) {
        if (this.status.statusCode() != PolicyStatus.StatusCode.LAPSED) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "Only LAPSED policies can be reinstated");
        }
        AggregateLifecycle.apply(new PolicyReinstatedEvent(this.policyId, command.reason(), LocalDateTime.now(),
                command.operatorId(), this.tenantId));
    }

    /**
     * 终止保单（保全域触发/退保）
     */
    @CommandHandler
    public void handle(TerminatePolicyCommand command) {
        PolicyStatus.StatusCode currentStatus = this.status.statusCode();
        if (currentStatus != PolicyStatus.StatusCode.EFFECTIVE && currentStatus != PolicyStatus.StatusCode.SUSPENDED
                && currentStatus != PolicyStatus.StatusCode.LAPSED) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION",
                    "Only EFFECTIVE, SUSPENDED or LAPSED policies can be terminated");
        }
        AggregateLifecycle.apply(new PolicyTerminatedEvent(this.policyId, command.reason(), command.terminationReason(),
                LocalDateTime.now(), command.operatorId(), this.tenantId));
    }

    /**
     * 应用保单批改（数据/要素类批改回写，事件溯源）
     * <p>
     * 仅 EFFECTIVE 保单可批改（与保全域创建校验对齐）；批改类型必须不改状态（守恒）。 落为不可变批单记录并递增版本号，不触碰保单状态机。
     * </p>
     */
    @CommandHandler
    public void handle(ApplyPolicyEndorsementCommand command) {
        if (this.status.statusCode() != PolicyStatus.StatusCode.EFFECTIVE) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "Only EFFECTIVE policies can be endorsed");
        }
        if (command.updateType() == null || command.updateType().changesStatus()) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "状态类变更不得走批改入口");
        }
        if (command.endorsementNo() == null || command.endorsementNo().isBlank()) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "批单号不能为空");
        }
        // 幂等保护：同一来源保全案件不重复批改（Kafka at-least-once 重投兜底）
        if (command.sourceMaintenanceId() != null && this.endorsements != null && this.endorsements.stream()
                .anyMatch(e -> command.sourceMaintenanceId().equals(e.sourceMaintenanceId()))) {
            throw new PolicyBusinessRuleException("POLICY_ENDORSEMENT_DUPLICATE",
                    "保全案件 " + command.sourceMaintenanceId() + " 已批改，忽略重复请求");
        }
        // versionAfter 仅作审计快照（事件溯源权威版本由 ESH 重放 incrementVersion 产生）
        int versionAfter = (this.basicInfo != null ? this.basicInfo.policyVersion() : 0) + 1;
        AggregateLifecycle.apply(new PolicyEndorsedEvent(this.policyId, command.endorsementNo(), command.updateType(),
                command.updateType().getCategory(), versionAfter, command.endorsementEffectiveDate(),
                command.changeSummary(), command.originalSnapshot(), command.updateType().needsPremiumRecalc(),
                command.sourceMaintenanceId(), LocalDateTime.now(), command.operatorId(), this.tenantId));
    }

    /**
     * 取消保单（仅未生效保单）
     */
    @CommandHandler
    public void handle(CancelPolicyCommand command) {
        if (this.status.statusCode() != PolicyStatus.StatusCode.NOT_EFFECTIVE) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION",
                    "Only NOT_EFFECTIVE policies can be cancelled");
        }
        AggregateLifecycle.apply(new PolicyCancelledEvent(this.policyId, LocalDateTime.now(), this.tenantId));
    }

    // ==================== EventSourcingHandler ====================

    @EventSourcingHandler
    public void on(PolicyCreatedEvent event) {
        this.policyId = event.policyId();
        this.policyNo = event.policyNo();
        this.policyForm = event.policyForm();
        this.productId = event.productId();
        this.sumInsured = event.sumInsured();
        this.insuranceType = event.insuranceType();
        this.tenantId = event.tenantId();
        this.createTime = LocalDateTime.now();
        this.insuranceProducts = new ArrayList<>();
        this.subjects = new ArrayList<>();
        this.deductibleRules = new ArrayList<>();
        this.paymentRecords = new ArrayList<>();
        this.policyDocuments = new ArrayList<>();
        this.endorsements = new ArrayList<>();
        this.status = event.status();
        this.policyRelation = new PolicyRelation(PolicyEnum.PolicyLevel.INDEPENDENT, null, 0, null);
        this.basicInfo = new PolicyBasicInfo(null, 0, event.premium(), event.effectiveDate(), event.expiryDate(), 0,
                null);
        // 初始化空被保险人清单，支撑团单/家庭险出单后的成员动态增减（4.5/4.6）
        this.insuredPartyList = new InsuredPartyList(this.policyId, null, new ArrayList<>(), new ArrayList<>());
    }

    @EventSourcingHandler
    public void on(PolicyEndorsedEvent event) {
        // 版本真相唯一由此处递增产生（事件 versionAfter 仅审计）；批单记录取递增后的版本号
        incrementVersion();
        int currentVersion = this.basicInfo != null ? this.basicInfo.policyVersion() : event.versionAfter();
        if (this.endorsements == null) {
            this.endorsements = new ArrayList<>();
        }
        this.endorsements.add(new Endorsement(event.endorsementNo(), event.updateType(), event.category(),
                currentVersion, event.endorsementEffectiveDate(), event.changeSummary(), event.originalSnapshot(),
                event.requiresPremiumRecalc(), event.sourceMaintenanceId(), event.endorsedAt(), event.operatorId()));
    }

    @EventSourcingHandler
    public void on(PolicyIssuedEvent event) {
        this.issueTime = event.issueTime();
        // 签发后保单仍为 NOT_EFFECTIVE，签发 ≠ 生效
    }

    @EventSourcingHandler
    public void on(PolicyActivatedEvent event) {
        this.status = this.status.transitionStatus(PolicyStatus.StatusCode.EFFECTIVE, "保单生效",
                PolicyConstants.POLICY_SYSTEM);
    }

    @EventSourcingHandler
    public void on(PolicySuspendedEvent event) {
        this.status = this.status.transitionStatus(PolicyStatus.StatusCode.SUSPENDED, "保单暂停",
                PolicyConstants.POLICY_SYSTEM);
    }

    @EventSourcingHandler
    public void on(PolicyResumedEvent event) {
        this.status = this.status.transitionStatus(PolicyStatus.StatusCode.EFFECTIVE, "保单恢复",
                PolicyConstants.POLICY_SYSTEM);
    }

    @EventSourcingHandler
    public void on(PolicyLapsedEvent event) {
        this.status = this.status.transitionStatus(PolicyStatus.StatusCode.LAPSED, event.reason(), event.operatorId());
    }

    @EventSourcingHandler
    public void on(PolicyReinstatedEvent event) {
        this.status = this.status.transitionStatus(PolicyStatus.StatusCode.EFFECTIVE, event.reason(),
                event.operatorId());
    }

    @EventSourcingHandler
    public void on(PolicyTerminatedEvent event) {
        this.status = this.status.transitionStatus(PolicyStatus.StatusCode.TERMINATED, event.reason(),
                event.operatorId());
        // 保单终止（身故给付/退保等）联动中止年金给付计划：被保险人身故后不再有生存年金，
        // 避免读模型年金计划停留 PAYING 与保单已终止的语义不一致。
        if (this.annuityPayoutPlan != null) {
            this.annuityPayoutPlan = this.annuityPayoutPlan.stop();
        }
    }

    @EventSourcingHandler
    public void on(PolicyExpiredEvent event) {
        this.status = this.status.transitionStatus(PolicyStatus.StatusCode.EXPIRED, "保单满期",
                PolicyConstants.POLICY_SYSTEM);
    }

    @EventSourcingHandler
    public void on(PolicyCancelledEvent event) {
        this.status = this.status.transitionStatus(PolicyStatus.StatusCode.CANCELLED, "保单取消",
                PolicyConstants.POLICY_SYSTEM);
    }

    // ==================== 业务方法 ====================

    /**
     * 保单到期失效（定时任务触发）
     */
    public void expire() {
        if (this.status.statusCode() != PolicyStatus.StatusCode.EFFECTIVE) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "Only EFFECTIVE policies can expire");
        }
        AggregateLifecycle.apply(new PolicyExpiredEvent(this.policyId, LocalDateTime.now(), this.tenantId));
    }

    /**
     * 记录缴费（计费域触发）
     */
    public void recordPayment(PaymentRecord paymentRecord) {
        this.paymentRecords.add(paymentRecord);
        // 更新缴费状态
        if (this.premiumPlan != null) {
            // 根据累计缴费金额判断状态
            this.premiumPlan = new PremiumPlan(this.premiumPlan.premiumAmount(), this.premiumPlan.paymentMethod(),
                    this.premiumPlan.paymentCycle(), this.premiumPlan.premiumDueDate(), PremiumPlan.PaymentStatus.PAID);
        }
        AggregateLifecycle.apply(new PolicyPaymentRecordedEvent(this.policyId, paymentRecord.paymentId(),
                paymentRecord.paymentAmount().value(), paymentRecord.paymentAmount().currency(),
                paymentRecord.paymentTime(), this.tenantId));
    }

    /**
     * 更新保单状态（通用方法，供父子保单联动使用）
     * <p>
     * 仅驱动本聚合状态机。父→子的跨聚合状态级联不在聚合内完成（聚合不可变更兄弟聚合）， 由 PolicyTerminated/Suspended
     * 等事件触发的级联编排器对每个子保单单独下发命令实现。
     * </p>
     */
    public void updatePolicyStatus(PolicyStatus.StatusCode newStatusCode, String changeReason, String operatorId) {
        this.status = this.status.transitionStatus(newStatusCode, changeReason, operatorId);
    }

    /**
     * 挂载子保单（团单主子联动，事件溯源）
     */
    @CommandHandler
    public void handle(LinkSubPolicyCommand command) {
        if (this.policyRelation != null && this.policyRelation.isChild()) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "子保单不可再挂载子保单");
        }
        PolicyRelation current = this.policyRelation != null ? this.policyRelation : PolicyRelation.independent();
        PolicyRelation linked = current.linkChild();
        AggregateLifecycle.apply(new SubPolicyLinkedEvent(this.policyId, command.childPolicyId(), command.groupId(),
                linked.subPolicyCount(), LocalDateTime.now(), command.operatorId(), this.tenantId));
    }

    @EventSourcingHandler
    public void on(SubPolicyLinkedEvent event) {
        PolicyRelation current = this.policyRelation != null ? this.policyRelation : PolicyRelation.independent();
        this.policyRelation = current.linkChild();
    }

    /**
     * 挂接投资账户（投连/万能保单出单后关联投资账户，事件溯源）
     * <p>
     * 仅投连类形态可挂接（{@code PolicyForm.isInvestmentLinked()}）；重复挂接幂等返回。
     * </p>
     */
    @CommandHandler
    public void handle(LinkInvestmentAccountCommand command) {
        ensureInvestmentLinked();
        if (this.investmentAccountId != null) {
            return;
        }
        AggregateLifecycle.apply(new InvestmentAccountLinkedEvent(this.policyId, command.investmentAccountId(),
                LocalDateTime.now(), command.operatorId(), this.tenantId));
    }

    @EventSourcingHandler
    public void on(InvestmentAccountLinkedEvent event) {
        this.investmentAccountId = event.investmentAccountId();
    }

    /**
     * 回写投资账户价值（投连/万能保单账户价值变更后由投资域回写，事件溯源）
     * <p>
     * 仅投连类形态且已挂接投资账户可回写；回写账户须与已挂接账户一致。账户价值为最终一致的展示型数据，
     * 允许 0（清仓/赎回后），故仅校验非空非负。
     * </p>
     */
    @CommandHandler
    public void handle(UpdateAccountValueCommand command) {
        ensureInvestmentLinked();
        if (this.investmentAccountId == null) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "保单尚未挂接投资账户，不可回写账户价值");
        }
        if (command.accountId() != null && !this.investmentAccountId.equals(command.accountId())) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION",
                    "回写账户与已挂接账户不一致：已挂接=" + this.investmentAccountId + "，回写=" + command.accountId());
        }
        if (command.accountValue() == null || command.accountValue().compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "账户价值不能为空或负数");
        }
        AggregateLifecycle.apply(new AccountValueUpdatedEvent(this.policyId, this.investmentAccountId,
                command.accountValue(), command.currency(), LocalDateTime.now(), this.tenantId));
    }

    @EventSourcingHandler
    public void on(AccountValueUpdatedEvent event) {
        this.investmentAccountValue = event.accountValue();
    }

    /**
     * 新增被保险人（团单加保 / 家庭险增员，事件溯源）
     * <p>
     * 仅团单/家庭险可增减成员；被保单须处于有效态（EFFECTIVE）方可加保。家庭险须提供家庭成员关系。
     * </p>
     */
    @CommandHandler
    public void handle(AddInsuredMemberCommand command) {
        ensureMemberModifiable();
        if (command.member() == null) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "新增被保险人信息不能为空");
        }
        if (this.policyForm == PolicyForm.FAMILY && command.familyRelation() == null) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "家庭险新增成员必须指定家庭成员关系");
        }
        AggregateLifecycle.apply(new InsuredMemberAddedEvent(this.policyId, command.member(), command.familyRelation(),
                LocalDateTime.now(), command.operatorId(), this.tenantId));
    }

    @EventSourcingHandler
    public void on(InsuredMemberAddedEvent event) {
        InsuredPartyList.InsuredInfo member = event.member();
        // 家庭险场景以事件携带的家庭关系覆盖成员关系，保证清单内关系一致
        if (event.familyRelation() != null) {
            member = new InsuredPartyList.InsuredInfo(member.insuredId(), member.name(), member.certType(),
                    member.certNo(), member.age(), member.gender(), event.familyRelation());
        }
        if (this.insuredPartyList != null) {
            this.insuredPartyList = this.insuredPartyList.addInsured(member);
        }
    }

    /**
     * 移除被保险人（团单减保 / 家庭险减员，事件溯源）
     */
    @CommandHandler
    public void handle(RemoveInsuredMemberCommand command) {
        ensureMemberModifiable();
        // 清单增删的不变量（存在性/非空）由 InsuredPartyList 守护，转译为业务异常
        try {
            if (this.insuredPartyList != null) {
                this.insuredPartyList.removeInsured(command.insuredId());
            }
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", ex.getMessage());
        }
        AggregateLifecycle.apply(new InsuredMemberRemovedEvent(this.policyId, command.insuredId(), command.reason(),
                LocalDateTime.now(), command.operatorId(), this.tenantId));
    }

    @EventSourcingHandler
    public void on(InsuredMemberRemovedEvent event) {
        if (this.insuredPartyList != null) {
            this.insuredPartyList = this.insuredPartyList.removeInsured(event.insuredId());
        }
    }

    /**
     * 启动年金给付期（年金保险专属，事件溯源）
     * <p>
     * 仅年金险种（{@link InsuranceProductType#ANNUITY}）的生效保单可进入给付期；重复启动被拒绝。
     * 启动后保单进入年金给付期，按频率周期性给付生存年金，<b>保单状态不变</b>（区别于身故给付终止保单）。
     * </p>
     */
    @CommandHandler
    public void handle(StartAnnuityPayoutCommand command) {
        if (this.status.statusCode() != PolicyStatus.StatusCode.EFFECTIVE) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "仅生效保单可启动年金给付");
        }
        if (this.insuranceType != InsuranceProductType.ANNUITY) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "非年金险种保单不可启动年金给付");
        }
        if (this.annuityPayoutPlan != null) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "年金给付期已启动，不可重复启动");
        }
        AggregateLifecycle.apply(new AnnuityPayoutStartedEvent(this.policyId, command.startDate(), command.frequency(),
                command.amountPerInstallment(), command.totalInstallments(), command.startDate(), command.operatorId(),
                LocalDateTime.now(), this.tenantId));
    }

    @EventSourcingHandler
    public void on(AnnuityPayoutStartedEvent event) {
        this.annuityPayoutPlan = AnnuityPayoutPlan.start(event.startDate(), event.frequency(),
                event.amountPerInstallment(), event.totalInstallments());
    }

    /**
     * 给付一期年金（年金给付期内定时触发，事件溯源）
     * <p>
     * 给付计划须处于给付中；每期给付使已给付期数递增、顺延下一给付日，给满约定期数后计划完成。
     * 年金给付<b>不改变保单状态</b>——保单在给付期内始终有效。
     * </p>
     */
    @CommandHandler
    public void handle(PayAnnuityBenefitCommand command) {
        if (this.annuityPayoutPlan == null) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "年金给付期未启动，不可给付");
        }
        // 计划状态/完成性不变量由 AnnuityPayoutPlan 守护，转译为业务异常
        AnnuityPayoutPlan paid;
        try {
            paid = this.annuityPayoutPlan.payNextInstallment(LocalDateTime.now());
        } catch (IllegalStateException ex) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", ex.getMessage());
        }
        AggregateLifecycle.apply(new AnnuityBenefitPaidEvent(this.policyId, paid.paidInstallments(),
                paid.installmentAmount(), paid.paidInstallments(), paid.nextPayoutDate(), paid.status(),
                command.operatorId(), LocalDateTime.now(), this.tenantId));
    }

    @EventSourcingHandler
    public void on(AnnuityBenefitPaidEvent event) {
        if (this.annuityPayoutPlan != null) {
            this.annuityPayoutPlan = this.annuityPayoutPlan.payNextInstallment(event.occurredAt());
        }
    }

    /**
     * 满期给付（两全险/生存给付型寿险专属，事件溯源）
     * <p>
     * 被保险人生存至保险期间届满，给付满期生存保险金后保单转满期（EXPIRED，终态）。仅生效保单可满期给付；
     * 满期给付金额须为正。区别于普通 {@code expire()}（仅止期到达无给付）。
     * </p>
     */
    @CommandHandler
    public void handle(MaturePolicyCommand command) {
        if (this.status.statusCode() != PolicyStatus.StatusCode.EFFECTIVE) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "仅生效保单可满期给付");
        }
        // 满期金仅生存给付型险种具备：两全险（ENDOWMENT）满期给付满期金；年金险满期给付完毕另经年金给付计划。
        // 定期寿险/终身寿险为身故给付型，无满期金。insuranceType 为 null（存量事件）时放行以兼容。
        if (this.insuranceType != null && this.insuranceType != InsuranceProductType.ENDOWMENT) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION",
                    "仅两全险(ENDOWMENT)可满期给付满期金，当前险种：" + this.insuranceType.getCode());
        }
        if (command.maturityBenefit() == null || command.maturityBenefit().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "满期给付金额必须大于零");
        }
        AggregateLifecycle.apply(new PolicyMaturedEvent(this.policyId, command.maturityBenefit(), command.operatorId(),
                LocalDateTime.now(), this.tenantId));
    }

    /**
     * 到期满期给付（定时任务专用，事件溯源）
     * <p>
     * 满期金额取聚合自身基本保额 {@code sumInsured}，其余规则同 {@link #handle(MaturePolicyCommand)}：
     * 仅生效两全险可满期给付，保额须为正。定时任务在保单止期到达时批量触发，无需调用方提供满期金额。
     * </p>
     */
    @CommandHandler
    public void handle(MatureDuePolicyCommand command) {
        if (this.status.statusCode() != PolicyStatus.StatusCode.EFFECTIVE) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "仅生效保单可满期给付");
        }
        if (this.insuranceType != null && this.insuranceType != InsuranceProductType.ENDOWMENT) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION",
                    "仅两全险(ENDOWMENT)可满期给付满期金，当前险种：" + this.insuranceType.getCode());
        }
        if (this.sumInsured == null || this.sumInsured.value().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "保单基本保额缺失或非正，不可满期给付");
        }
        AggregateLifecycle.apply(new PolicyMaturedEvent(this.policyId, this.sumInsured.value(), command.operatorId(),
                LocalDateTime.now(), this.tenantId));
    }

    @EventSourcingHandler
    public void on(PolicyMaturedEvent event) {
        this.status = this.status.transitionStatus(PolicyStatus.StatusCode.EXPIRED, "满期给付", event.operatorId());
    }

    /**
     * 保费豁免（寿险保费豁免条款，事件溯源）
     * <p>
     * 投保人/被保险人发生约定事件（身故/全残/重疾），豁免后续应缴保费。保单<b>保持 EFFECTIVE</b>、保障不变；
     * 重复豁免幂等拒绝。仅生效保单可豁免。
     * </p>
     */
    @CommandHandler
    public void handle(WaivePremiumCommand command) {
        if (this.status.statusCode() != PolicyStatus.StatusCode.EFFECTIVE) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "仅生效保单可办理保费豁免");
        }
        if (this.premiumWaived) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "保单已办理保费豁免，不可重复豁免");
        }
        if (command.reason() == null) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "保费豁免原因不能为空");
        }
        AggregateLifecycle.apply(new PremiumWaivedEvent(this.policyId, command.reason(), command.operatorId(),
                LocalDateTime.now(), this.tenantId));
    }

    @EventSourcingHandler
    public void on(PremiumWaivedEvent event) {
        this.premiumWaived = true;
        this.premiumWaiverReason = event.reason();
    }

    /**
     * 红利派发（分红险年度红利处理，事件溯源）
     * <p>
     * 分红型保单按保单年度派发红利并按领取方式处置。仅生效的分红型（{@code ParticipationType.PARTICIPATING}，
     * 由 product 侧配置决定，此处以红利金额为正校验）保单可派发；留存类领取方式（累积生息/购买交清增额）
     * 累加到累计红利，现金/抵缴方式不累加。
     * </p>
     */
    @CommandHandler
    public void handle(DistributeDividendCommand command) {
        if (this.status.statusCode() != PolicyStatus.StatusCode.EFFECTIVE) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "仅生效保单可派发红利");
        }
        // 投连/万能险为账户价值型产品，其收益走单位净值/结算利率（investment 域账户），非分红险红利机制，
        // 不可走本红利派发。分红资格（普通型是否分红）由 product 侧 ParticipationType 决定，
        // 保单聚合暂未承载该维度（需从 PolicyCreatedEvent 透传，属后续增强），当前先拦截账户价值型形态。
        if (this.policyForm != null && this.policyForm.isInvestmentLinked()) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION",
                    "投连/万能险不适用红利派发（其收益走账户价值/结算利率）");
        }
        if (command.dividendAmount() == null || command.dividendAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "红利金额必须大于零");
        }
        if (command.option() == null) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "红利领取方式不能为空");
        }
        // 留存类方式累加累计红利，现金/抵缴不累加
        java.math.BigDecimal currentAccumulated = this.accumulatedDividend != null
                ? this.accumulatedDividend.value() : java.math.BigDecimal.ZERO;
        java.math.BigDecimal newAccumulated = command.option().isRetained()
                ? currentAccumulated.add(command.dividendAmount()) : currentAccumulated;
        AggregateLifecycle.apply(new DividendDistributedEvent(this.policyId, command.dividendAmount(), command.option(),
                command.policyYear(), newAccumulated, command.operatorId(), LocalDateTime.now(), this.tenantId));
    }

    @EventSourcingHandler
    public void on(DividendDistributedEvent event) {
        // 累计红利以事件携带的累计值为准（留存类累加，现金/抵缴保持不变）
        String currency = this.sumInsured != null ? this.sumInsured.currency() : "CNY";
        this.accumulatedDividend = Money.of(event.accumulatedDividend(), currency);
    }

    /**
     * 形态校验：仅投连/万能保单可挂接投资账户。
     */
    private void ensureInvestmentLinked() {
        if (this.policyForm == null || !this.policyForm.isInvestmentLinked()) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "非投连/万能保单不可挂接投资账户");
        }
    }

    /**
     * 形态 + 状态校验：仅团单/家庭险的有效保单可动态增减被保险人。
     */
    private void ensureMemberModifiable() {
        if (this.policyForm == null || !(this.policyForm.isGroup() || this.policyForm.isFamily())) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "仅团单/家庭险可动态增减被保险人");
        }
        if (this.status.statusCode() != PolicyStatus.StatusCode.EFFECTIVE) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "仅生效保单可增减被保险人");
        }
    }

    /**
     * 版本递增（保全域数据变更后调用）
     */
    public void incrementVersion() {
        if (this.basicInfo != null) {
            this.basicInfo = this.basicInfo.createNewVersion();
        }
    }

    // ==================== 内部方法 ====================

    private void generateDocument() {
        PolicyDocument document = new PolicyDocument("doc-" + LocalDateTime.now(), "E-" + this.policyNo.value(),
                "P-" + this.policyNo.value(), LocalDateTime.now(), PolicyEnum.SignatureStatus.UNSIGNED,
                "http://docs.titanium.com/policies/" + this.policyNo.value());
        this.policyDocuments.add(document);
    }

    protected Policy() {
    }
}
