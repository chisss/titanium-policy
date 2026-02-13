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
import com.titanium.policy.command.ActivatePolicyCommand;
import com.titanium.policy.command.CancelPolicyCommand;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.command.CreatePolicyDirectlyCommand;
import com.titanium.policy.command.IssuePolicyCommand;
import com.titanium.policy.command.ResumePolicyCommand;
import com.titanium.policy.command.SuspendPolicyCommand;
import com.titanium.policy.command.TerminatePolicyCommand;
import com.titanium.policy.common.constant.PolicyConstants;
import com.titanium.policy.entity.InsuranceProduct;
import com.titanium.policy.entity.PaymentRecord;
import com.titanium.policy.entity.Subject;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.event.PolicyActivatedEvent;
import com.titanium.policy.event.PolicyCancelledEvent;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.event.PolicyExpiredEvent;
import com.titanium.policy.event.PolicyIssuedEvent;
import com.titanium.policy.event.PolicyPaymentRecordedEvent;
import com.titanium.policy.event.PolicyResumedEvent;
import com.titanium.policy.event.PolicySuspendedEvent;
import com.titanium.policy.event.PolicyTerminatedEvent;
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
@Builder(builderMethodName = "builder")
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
    private String                 policyForm;
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
            throw new IllegalArgumentException("Only NOT_EFFECTIVE policies can be issued");
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
            throw new IllegalArgumentException("Only NOT_EFFECTIVE policies can be activated");
        }
        // 校验首期保费是否已缴纳
        if (this.premiumPlan != null && this.premiumPlan.paymentStatus() == PremiumPlan.PaymentStatus.UNPAID) {
            throw new IllegalArgumentException("First premium must be paid before activation");
        }
        // 校验保障起期是否到达
        if (this.basicInfo != null && this.basicInfo.insurancePeriodStart() != null
                && this.basicInfo.insurancePeriodStart().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Insurance period has not started yet");
        }
        AggregateLifecycle.apply(new PolicyActivatedEvent(this.policyId, LocalDateTime.now(), this.tenantId));
    }

    /**
     * 暂停保单（保全域触发）
     */
    @CommandHandler
    public void handle(SuspendPolicyCommand command) {
        if (this.status.statusCode() != PolicyStatus.StatusCode.EFFECTIVE) {
            throw new IllegalArgumentException("Only EFFECTIVE policies can be suspended");
        }
        AggregateLifecycle.apply(new PolicySuspendedEvent(this.policyId, LocalDateTime.now(), this.tenantId));
    }

    /**
     * 恢复保单（保全域触发）
     */
    @CommandHandler
    public void handle(ResumePolicyCommand command) {
        if (this.status.statusCode() != PolicyStatus.StatusCode.SUSPENDED) {
            throw new IllegalArgumentException("Only SUSPENDED policies can be resumed");
        }
        AggregateLifecycle.apply(new PolicyResumedEvent(this.policyId, LocalDateTime.now(), this.tenantId));
    }

    /**
     * 终止保单（保全域触发/退保）
     */
    @CommandHandler
    public void handle(TerminatePolicyCommand command) {
        PolicyStatus.StatusCode currentStatus = this.status.statusCode();
        if (currentStatus != PolicyStatus.StatusCode.EFFECTIVE && currentStatus != PolicyStatus.StatusCode.SUSPENDED) {
            throw new IllegalArgumentException("Only EFFECTIVE or SUSPENDED policies can be terminated");
        }
        AggregateLifecycle.apply(new PolicyTerminatedEvent(this.policyId, command.reason(),
                command.terminationReason() != null ? command.terminationReason().getCode() : null, LocalDateTime.now(),
                command.operatorId(), this.tenantId));
    }

    /**
     * 取消保单（仅未生效保单）
     */
    @CommandHandler
    public void handle(CancelPolicyCommand command) {
        if (this.status.statusCode() != PolicyStatus.StatusCode.NOT_EFFECTIVE) {
            throw new IllegalArgumentException("Only NOT_EFFECTIVE policies can be cancelled");
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
        this.status = event.status();
        this.policyRelation = new PolicyRelation(PolicyEnum.PolicyLevel.INDEPENDENT, null, 0, null);
        this.basicInfo = new PolicyBasicInfo(null, 0, event.premium(), event.effectiveDate(), event.expiryDate(), 0,
                null);
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
    public void on(PolicyTerminatedEvent event) {
        this.status = this.status.transitionStatus(PolicyStatus.StatusCode.TERMINATED, event.reason(),
                event.operatorId());
    }

    @EventSourcingHandler
    public void on(PolicyExpiredEvent event) {
        this.status = this.status.transitionStatus(PolicyStatus.StatusCode.EXPIRED, "保单到期失效",
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
            throw new IllegalArgumentException("Only EFFECTIVE policies can expire");
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
     */
    public void updatePolicyStatus(PolicyStatus.StatusCode newStatusCode, String changeReason, String operatorId) {
        this.status = this.status.transitionStatus(newStatusCode, changeReason, operatorId);
        if (this.policyRelation != null && this.policyRelation.policyLevel() == PolicyEnum.PolicyLevel.PARENT) {
            this.policyRelation.syncParentStatus(newStatusCode);
        }
    }

    /**
     * 关联子保单
     */
    public void linkSubPolicy(String childPolicyId) {
        if (this.policyRelation.policyLevel() != PolicyEnum.PolicyLevel.PARENT) {
            if (this.policyRelation.policyLevel() == PolicyEnum.PolicyLevel.INDEPENDENT) {
                this.policyRelation = new PolicyRelation(PolicyEnum.PolicyLevel.PARENT, null,
                        this.policyRelation.subPolicyCount() + 1, this.policyRelation.groupId());
            } else {
                throw new IllegalArgumentException("Only parent policies can link sub policies");
            }
        } else {
            this.policyRelation = new PolicyRelation(this.policyRelation.policyLevel(),
                    this.policyRelation.parentPolicyId(), this.policyRelation.subPolicyCount() + 1,
                    this.policyRelation.groupId());
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
        PolicyDocument document = new PolicyDocument("doc-" + LocalDateTime.now().toString(),
                "E-" + this.policyNo.value(), "P-" + this.policyNo.value(), LocalDateTime.now(),
                PolicyEnum.SignatureStatus.UNSIGNED, "http://docs.titanium.com/policies/" + this.policyNo.value());
        this.policyDocuments.add(document);
    }

    protected Policy() {
    }
}
