package com.titanium.policy.aggregate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.policy.command.ActivatePolicyCommand;
import com.titanium.policy.command.ApplyPolicyEndorsementCommand;
import com.titanium.policy.command.CancelPolicyCommand;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.command.CreatePolicyDirectlyCommand;
import com.titanium.policy.command.IssuePolicyCommand;
import com.titanium.policy.command.LapsePolicyCommand;
import com.titanium.policy.command.LinkSubPolicyCommand;
import com.titanium.policy.command.ReinstatePolicyCommand;
import com.titanium.policy.command.ResumePolicyCommand;
import com.titanium.policy.command.SuspendPolicyCommand;
import com.titanium.policy.command.TerminatePolicyCommand;
import com.titanium.policy.common.constant.PolicyConstants;
import com.titanium.policy.entity.Endorsement;
import com.titanium.policy.entity.InsuranceProduct;
import com.titanium.policy.entity.PaymentRecord;
import com.titanium.policy.entity.Subject;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.event.PolicyActivatedEvent;
import com.titanium.policy.event.PolicyCancelledEvent;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.event.PolicyEndorsedEvent;
import com.titanium.policy.event.PolicyExpiredEvent;
import com.titanium.policy.event.PolicyIssuedEvent;
import com.titanium.policy.event.PolicyLapsedEvent;
import com.titanium.policy.event.PolicyPaymentRecordedEvent;
import com.titanium.policy.event.PolicyReinstatedEvent;
import com.titanium.policy.event.PolicyResumedEvent;
import com.titanium.policy.event.PolicySuspendedEvent;
import com.titanium.policy.event.PolicyTerminatedEvent;
import com.titanium.policy.event.SubPolicyLinkedEvent;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.valueobject.DeductibleRule;
import com.titanium.policy.valueobject.PolicyBasicInfo;
import com.titanium.policy.valueobject.PolicyDocument;
import com.titanium.policy.valueobject.PolicyNo;
import com.titanium.policy.valueobject.PolicyRelation;
import com.titanium.policy.valueobject.PolicyStatus;
import com.titanium.policy.valueobject.PremiumPlan;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 正式保单聚合根
 * <p>
 * 保单域核心聚合根，管理正式保单全生命周期：创建 → 签发 → 生效 → 暂停/恢复/终止/到期。 保单域是"执行者"，被动接收保全域的状态变更指令。
 * </p>
 */
@Aggregate
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Policy {
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
    /** 创建时间 */
    private LocalDateTime          createTime;
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
    /** 缴费记录列表 */
    private List<PaymentRecord>    paymentRecords;
    /** 保单单证列表 */
    private List<PolicyDocument>   policyDocuments;
    /** 批单列表（数据/要素类批改留痕） */
    private List<Endorsement>      endorsements;
    /** 保单状态 */
    private PolicyStatus           status;
    /** 租户ID */
    private String                 tenantId;

    // ==================== CommandHandler ====================

    /**
     * 创建保单（从投保单+核保结果创建）
     */
    @CommandHandler
    public Policy(CreatePolicyCommand command) {
        AggregateLifecycle
                .apply(new PolicyCreatedEvent(command.policyId(), new PolicyNo(command.policyNo()), command.startDate(),
                        command.endDate(), command.premium(), new PolicyStatus(PolicyStatus.StatusCode.NOT_EFFECTIVE,
                                LocalDateTime.now(), "创建保单", PolicyConstants.POLICY_SYSTEM),
                        new ArrayList<>(), command.tenantId()));
    }

    /**
     * 一步出单直接创建保单
     */
    @CommandHandler
    public Policy(CreatePolicyDirectlyCommand command) {
        AggregateLifecycle.apply(new PolicyCreatedEvent(command.policyId(), new PolicyNo(command.policyNo()),
                command.insurancePeriodStart(), command.insurancePeriodEnd(), command.totalPremium(),
                new PolicyStatus(PolicyStatus.StatusCode.NOT_EFFECTIVE, LocalDateTime.now(), "一步出单创建保单",
                        PolicyConstants.POLICY_SYSTEM),
                new ArrayList<>(), command.tenantId()));
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
        AggregateLifecycle.apply(new PolicyIssuedEvent(this.policyId, this.policyNo.value(), LocalDateTime.now(),
                command.operatorId(), this.tenantId));
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
     * 仅 EFFECTIVE 保单可批改（与保全域创建校验对齐）；批改类型必须不改状态（守恒）。
     * 落为不可变批单记录并递增版本号，不触碰保单状态机。
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
        if (command.sourceMaintenanceId() != null && this.endorsements != null
                && this.endorsements.stream()
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
        this.status = this.status.transitionStatus(PolicyStatus.StatusCode.LAPSED, event.reason(),
                event.operatorId());
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
     * 仅驱动本聚合状态机。父→子的跨聚合状态级联不在聚合内完成（聚合不可变更兄弟聚合），
     * 由 PolicyTerminated/Suspended 等事件触发的级联编排器对每个子保单单独下发命令实现。
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
     * 版本递增（保全域数据变更后调用）
     */
    public void incrementVersion() {
        if (this.basicInfo != null) {
            this.basicInfo = this.basicInfo.createNewVersion();
        }
    }

    // ==================== 内部方法 ====================

    private void generateDocument() {
        PolicyDocument document = new PolicyDocument("doc-" + LocalDateTime.now().toString(),
                "E-" + this.policyNo.value(), "P-" + this.policyNo.value(), LocalDateTime.now(),
                PolicyEnum.SignatureStatus.UNSIGNED, "http://docs.titanium.com/policies/" + this.policyNo.value());
        this.policyDocuments.add(document);
    }

    protected Policy() {
    }
}
