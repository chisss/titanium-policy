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
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.SalesChannel;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.command.ConvertProposalCommand;
import com.titanium.policy.command.CreateProposalCommand;
import com.titanium.policy.command.SubmitProposalCommand;
import com.titanium.policy.command.VoidProposalCommand;
import com.titanium.policy.entity.proposal.ProposalHolder;
import com.titanium.policy.entity.proposal.ProposalLine;
import com.titanium.policy.entity.proposal.ProposalSubject;
import com.titanium.policy.event.proposal.ProposalConvertedEvent;
import com.titanium.policy.event.proposal.ProposalCreatedEvent;
import com.titanium.policy.event.proposal.ProposalSubmittedEvent;
import com.titanium.policy.event.proposal.ProposalVoidedEvent;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.valueobject.proposal.ProposalBasicInfo;
import com.titanium.policy.valueobject.proposal.ProposalStatus;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * 投保意向单聚合根
 * <p>
 * 作为投保意向单聚合的唯一对外交互入口，协调内部组件动作，记录客户初步投保意愿。 状态机：DRAFT → SUBMITTED →
 * CONVERTED_TO_APPLICATION / VOIDED
 * </p>
 */
@Aggregate
@Getter
@SuperBuilder(toBuilder = true)
public class Proposal extends BaseAggregate {
    /** 聚合根唯一标识 */
    @AggregateIdentifier
    private String                proposalId;
    /** 意向单编号 */
    private String                proposalNo;
    /** 保单形态：个单/团单/父子 */
    private PolicyForm            policyForm;
    /** 销售渠道 */
    private SalesChannel          channel;
    /** 意向单基本信息 */
    private ProposalBasicInfo     basicInfo;
    /**
     * 意向险种段列表（意向阶段的 L2，轻量）
     * <p>
     * 客户在 App 勾选「重疾 + 医疗 + 意外」组合时，意向单阶段就已是多险种。取代
     * {@code basicInfo.expectedProductCode}（单值 String，无法表达组合意图）。
     * 转投保单时精化为 {@code InsuranceLine}。
     * </p>
     */
    private List<ProposalLine>    proposalLines;
    /** 申请人列表 */
    private List<ProposalHolder>  applicants;
    /** 标的列表 */
    private List<ProposalSubject> subjects;
    /** 意向单状态 */
    private ProposalStatus        status;
    /** 险种三级分类（主险冗余，源头捕获，可空以兼容存量事件） */
    private InsuranceProductType  insuranceType;
    /** 出单业务流水号（幂等与进度追溯，可空） */
    private String                bizNo;
    /** 营销包ID（弱引用，可空） */
    private String                marketPackageId;

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
                command.proposalLines(), command.insuranceType(), command.bizNo(), command.marketPackageId(),
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
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION",
                    "Only draft or submitted proposals can be voided");
        }
        AggregateLifecycle.apply(new ProposalVoidedEvent(command.proposalId(), command.changeReason(),
                LocalDateTime.now(), this.tenantId));
    }

    /**
     * 意向单转投保单 - 命令处理器（事件溯源）
     * <p>
     * 仅已提交（SUBMITTED）意向单可转换，发布 {@code ProposalConvertedEvent} 流转为
     * {@code CONVERTED_TO_APPLICATION}。与纯对象方法 {@link #convertToApplication(String)} 语义一致，
     * 补齐事件溯源写侧（此前仅有纯对象方法，读模型投影已就绪但无命令触发）。
     * </p>
     */
    @CommandHandler
    public void handle(ConvertProposalCommand command) {
        if (this.status.statusCode() != ProposalStatus.StatusCode.SUBMITTED) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION",
                    "Only submitted proposals can be converted to application");
        }
        AggregateLifecycle.apply(new ProposalConvertedEvent(command.proposalId(), command.changeReason(),
                LocalDateTime.now(), this.tenantId));
    }

    // ==================== EventSourcingHandler ====================

    @EventSourcingHandler
    public void on(ProposalCreatedEvent event) {
        this.proposalId = event.proposalId();
        this.proposalNo = event.proposalNo();
        this.policyForm = event.policyForm();
        this.channel = event.channel();
        this.insuranceType = event.insuranceType();
        this.tenantId = event.tenantId();
        this.createTime = event.createTime();
        this.updateTime = event.createTime();
        this.applicants = new ArrayList<>();
        this.subjects = new ArrayList<>();
        // 意向险种段（L2 轻量）：事件携带则落地，缺失时置空列表（兼容存量事件）
        this.proposalLines = event.proposalLines() != null
                ? new ArrayList<>(event.proposalLines())
                : new ArrayList<>();
        this.bizNo = event.bizNo();
        this.marketPackageId = event.marketPackageId();
        this.status = new ProposalStatus(ProposalStatus.StatusCode.DRAFT, event.createTime(), "创建草稿");
        this.basicInfo = new ProposalBasicInfo(event.customerId(),
                event.intendedSumInsured() != null ? Money.of(event.intendedSumInsured(), "CNY") : null,
                event.intendedPremium() != null ? Money.of(event.intendedPremium(), "CNY") : null,
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
     * 创建意向单草稿（纯对象工厂，供应用层/单元测试以非事件溯源方式构建）
     *
     * @param proposalId 意向单ID
     * @param proposalNo 意向单编号
     * @param policyForm 保单形态
     * @param channel 销售渠道
     * @param basicInfo 基本信息
     * @param tenantId 租户ID
     * @return 草稿状态的意向单
     */
    public static Proposal createDraft(String proposalId, String proposalNo, PolicyForm policyForm,
                                       SalesChannel channel, ProposalBasicInfo basicInfo, String tenantId) {
        LocalDateTime now = LocalDateTime.now();
        Proposal proposal = new Proposal();
        proposal.proposalId = proposalId;
        proposal.proposalNo = proposalNo;
        proposal.policyForm = policyForm;
        proposal.channel = channel;
        proposal.basicInfo = basicInfo;
        proposal.tenantId = tenantId;
        proposal.createTime = now;
        proposal.updateTime = now;
        proposal.applicants = new ArrayList<>();
        proposal.subjects = new ArrayList<>();
        proposal.status = new ProposalStatus(ProposalStatus.StatusCode.DRAFT, now, "创建草稿");
        return proposal;
    }

    /**
     * 提交意向单（纯对象方法）：校验申请人/标的后流转为已提交
     *
     * @param changeReason 变更原因
     */
    public void submitProposal(String changeReason) {
        validateApplicants();
        validateSubjects();
        this.status = this.status.transitionStatus(ProposalStatus.StatusCode.SUBMITTED, changeReason);
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 作废意向单（纯对象方法）
     *
     * @param changeReason 变更原因
     */
    public void voidProposal(String changeReason) {
        ProposalStatus.StatusCode currentStatus = this.status.statusCode();
        if (currentStatus != ProposalStatus.StatusCode.DRAFT && currentStatus != ProposalStatus.StatusCode.SUBMITTED) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION",
                    "Only draft or submitted proposals can be voided");
        }
        this.status = this.status.transitionStatus(ProposalStatus.StatusCode.VOIDED, changeReason);
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 转为投保单 - 校验状态并流转（纯对象方法）
     *
     * @param changeReason 转换原因
     */
    public void convertToApplication(String changeReason) {
        if (this.status.statusCode() != ProposalStatus.StatusCode.SUBMITTED) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION",
                    "Only submitted proposals can be converted to application");
        }
        this.status = this.status.transitionStatus(ProposalStatus.StatusCode.CONVERTED_TO_APPLICATION, changeReason);
        this.updateTime = LocalDateTime.now();
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
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "Basic info cannot be null");
        }
        if (basicInfo.customerId() == null || basicInfo.customerId().isEmpty()) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "Customer ID cannot be null or empty");
        }
        if (basicInfo.expectedProductCode() == null || basicInfo.expectedProductCode().isEmpty()) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION",
                    "Expected product code cannot be null or empty");
        }
    }

    private void validateApplicants() {
        if (applicants.isEmpty()) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "At least one applicant is required");
        }
        for (ProposalHolder applicant : applicants) {
            if (!applicant.verifyApplicantInfo()) {
                throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION",
                        "Invalid applicant info: " + applicant.name());
            }
        }
    }

    private void validateSubjects() {
        if (subjects.isEmpty()) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "At least one subject is required");
        }
        if (PolicyForm.GROUP == this.policyForm && subjects.size() < 2) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "Group policy requires at least 2 subjects");
        }
    }

    private void ensureDraftStatus() {
        if (this.status.statusCode() != ProposalStatus.StatusCode.DRAFT) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "Only draft proposals can be modified");
        }
    }

    protected Proposal() {
    }
}
