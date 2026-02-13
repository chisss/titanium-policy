package com.titanium.policy.aggregate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import com.titanium.policy.command.CreateProposalCommand;
import com.titanium.policy.command.SubmitProposalCommand;
import com.titanium.policy.command.VoidProposalCommand;
import com.titanium.policy.entity.proposal.ProposalHolder;
import com.titanium.policy.entity.proposal.ProposalSubject;
import com.titanium.policy.event.proposal.ProposalConvertedEvent;
import com.titanium.policy.event.proposal.ProposalCreatedEvent;
import com.titanium.policy.event.proposal.ProposalSubmittedEvent;
import com.titanium.policy.event.proposal.ProposalVoidedEvent;
import com.titanium.policy.valueobject.Amount;
import com.titanium.policy.valueobject.proposal.ProposalBasicInfo;
import com.titanium.policy.valueobject.proposal.ProposalStatus;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 投保意向单聚合根
 * <p>
 * 作为投保意向单聚合的唯一对外交互入口，协调内部组件动作，记录客户初步投保意愿。 状态机：DRAFT → SUBMITTED →
 * CONVERTED_TO_APPLICATION / VOIDED
 * </p>
 */
@Aggregate
@Getter
@Builder(builderMethodName = "builder")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Proposal {
    /** 聚合根唯一标识 */
    @AggregateIdentifier
    private String                proposalId;
    /** 意向单编号 */
    private String                proposalNo;
    /** 保单形态：个单/团单/父子 */
    private String                policyForm;
    /** 父意向单ID */
    private String                parentProposalId;
    /** 销售渠道 */
    private String                channel;
    /** 创建时间 */
    private LocalDateTime         createTime;
    /** 更新时间 */
    private LocalDateTime         updateTime;
    /** 意向单基本信息 */
    private ProposalBasicInfo     basicInfo;
    /** 申请人列表 */
    private List<ProposalHolder>  applicants;
    /** 标的列表 */
    private List<ProposalSubject> subjects;
    /** 意向单状态 */
    private ProposalStatus        status;
    /** 租户ID */
    private String                tenantId;

    // ==================== CommandHandler ====================

    /**
     * 创建意向单 - 命令处理器
     */
    @CommandHandler
    public Proposal(CreateProposalCommand command) {
        AggregateLifecycle.apply(new ProposalCreatedEvent(command.proposalId(), command.proposalNo(),
                command.policyForm(), command.channel(), command.customerId(),
                command.intendedSumInsured() != null ? command.intendedSumInsured().value() : null,
                command.intendedPremium() != null ? command.intendedPremium().value() : null,
                command.insurancePeriodStart(), command.insurancePeriodEnd(), command.expectedProductCode(),
                LocalDateTime.now(), command.tenantId()));
    }

    /**
     * 提交意向单 - 命令处理器
     */
    @CommandHandler
    public void handle(SubmitProposalCommand command) {
        validateRequiredFields();
        validateApplicants();
        validateSubjects();
        AggregateLifecycle.apply(new ProposalSubmittedEvent(command.proposalId(), command.changeReason(),
                LocalDateTime.now(), this.tenantId));
    }

    /**
     * 作废意向单 - 命令处理器
     */
    @CommandHandler
    public void handle(VoidProposalCommand command) {
        ProposalStatus.StatusCode currentStatus = this.status.statusCode();
        if (currentStatus != ProposalStatus.StatusCode.DRAFT && currentStatus != ProposalStatus.StatusCode.SUBMITTED) {
            throw new IllegalArgumentException("Only draft or submitted proposals can be voided");
        }
        AggregateLifecycle.apply(new ProposalVoidedEvent(command.proposalId(), command.changeReason(),
                LocalDateTime.now(), this.tenantId));
    }

    // ==================== EventSourcingHandler ====================

    @EventSourcingHandler
    public void on(ProposalCreatedEvent event) {
        this.proposalId = event.proposalId();
        this.proposalNo = event.proposalNo();
        this.policyForm = event.policyForm();
        this.channel = event.channel();
        this.tenantId = event.tenantId();
        this.createTime = event.createTime();
        this.updateTime = event.createTime();
        this.applicants = new ArrayList<>();
        this.subjects = new ArrayList<>();
        this.status = new ProposalStatus(ProposalStatus.StatusCode.DRAFT, event.createTime(), "创建草稿");
        this.basicInfo = new ProposalBasicInfo(event.customerId(),
                event.intendedSumInsured() != null ? Amount.of(event.intendedSumInsured(), "CNY") : null,
                event.intendedPremium() != null ? Amount.of(event.intendedPremium(), "CNY") : null,
                event.insurancePeriodStart(), event.insurancePeriodEnd(), event.expectedProductCode());
    }

    @EventSourcingHandler
    public void on(ProposalSubmittedEvent event) {
        this.status = this.status.transitionStatus(ProposalStatus.StatusCode.SUBMITTED, event.changeReason());
        this.updateTime = event.submitTime();
    }

    @EventSourcingHandler
    public void on(ProposalConvertedEvent event) {
        this.status = this.status.transitionStatus(ProposalStatus.StatusCode.CONVERTED_TO_APPLICATION,
                event.changeReason());
        this.updateTime = event.convertTime();
    }

    @EventSourcingHandler
    public void on(ProposalVoidedEvent event) {
        this.status = this.status.transitionStatus(ProposalStatus.StatusCode.VOIDED, event.changeReason());
        this.updateTime = event.voidTime();
    }

    // ==================== 业务方法 ====================

    /**
     * 转为投保单 - 校验状态并发布转换事件
     *
     * @param changeReason 转换原因
     */
    public void convertToApplication(String changeReason) {
        if (this.status.statusCode() != ProposalStatus.StatusCode.SUBMITTED) {
            throw new IllegalArgumentException("Only submitted proposals can be converted to application");
        }
        AggregateLifecycle
                .apply(new ProposalConvertedEvent(this.proposalId, changeReason, LocalDateTime.now(), this.tenantId));
    }

    /**
     * 新增意向标的
     */
    public void addSubject(ProposalSubject subject) {
        ensureDraftStatus();
        this.subjects.add(subject);
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 新增申请人
     */
    public void addApplicant(ProposalHolder applicant) {
        ensureDraftStatus();
        this.applicants.add(applicant);
        this.updateTime = LocalDateTime.now();
    }

    // ==================== 校验方法 ====================

    private void validateRequiredFields() {
        if (basicInfo == null) {
            throw new IllegalArgumentException("Basic info cannot be null");
        }
        if (basicInfo.customerId() == null || basicInfo.customerId().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        if (basicInfo.expectedProductCode() == null || basicInfo.expectedProductCode().isEmpty()) {
            throw new IllegalArgumentException("Expected product code cannot be null or empty");
        }
    }

    private void validateApplicants() {
        if (applicants.isEmpty()) {
            throw new IllegalArgumentException("At least one applicant is required");
        }
        for (ProposalHolder applicant : applicants) {
            if (!applicant.verifyApplicantInfo()) {
                throw new IllegalArgumentException("Invalid applicant info: " + applicant.name());
            }
        }
    }

    private void validateSubjects() {
        if (subjects.isEmpty()) {
            throw new IllegalArgumentException("At least one subject is required");
        }
        if ("GROUP".equals(this.policyForm) && subjects.size() < 2) {
            throw new IllegalArgumentException("Group policy requires at least 2 subjects");
        }
    }

    private void ensureDraftStatus() {
        if (this.status.statusCode() != ProposalStatus.StatusCode.DRAFT) {
            throw new IllegalArgumentException("Only draft proposals can be modified");
        }
    }

    protected Proposal() {
    }
}
