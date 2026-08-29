package com.titanium.policy.aggregate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
                command.proposalLines(), command.proposalSubjects(), ProposalLine.resolveInsuranceType(command.insuranceType(),
                        command.proposalLines()), command.bizNo(), command.marketPackageId(),
                LocalDateTime.now(), command.tenantId(), command.insuredPartyList(), command.collectionMode(),
                command.channelInfo(), command.paymentMode(), command.premiumPaymentYears()));
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
        this.insuranceType = ProposalLine.resolveInsuranceType(event.insuranceType(), event.proposalLines());
        this.tenantId = event.tenantId();
        this.createTime = event.createTime();
        this.updateTime = event.createTime();
        this.applicants = initialApplicants(event);
        this.subjects = initialSubjects(event);
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

    /**
     * 统一出单携带完整参与方时，以投保人快照初始化意向申请人，保证自动提交满足聚合校验。
     */
    private ArrayList<ProposalHolder> initialApplicants(ProposalCreatedEvent event) {
        ArrayList<ProposalHolder> initial = new ArrayList<>();
        if (event.insuredPartyList() == null || event.insuredPartyList().holderInfo() == null) {
            return initial;
        }
        var holder = event.insuredPartyList().holderInfo();
        initial.add(new ProposalHolder(holder.holderId(), holder.name(), holder.certType(), holder.certNo(),
                holder.phone(), isHolderInsured(event)));
        return initial;
    }

    /**
     * 统一出单的标的由已校验的方案段承载；意向聚合只需记录至少一个可提交的轻量标的。
     */
    private ArrayList<ProposalSubject> initialSubjects(ProposalCreatedEvent event) {
        ArrayList<ProposalSubject> initial = new ArrayList<>();
        if (event.proposalSubjects() != null && !event.proposalSubjects().isEmpty()) {
            initial.addAll(event.proposalSubjects());
            return initial;
        }
        if (event.insuredPartyList() == null || event.insuredPartyList().insuredList() == null) {
            return initial;
        }
        for (var insured : event.insuredPartyList().insuredList()) {
            initial.add(new ProposalSubject(insured.insuredId(),
                    com.titanium.metadata.enums.insurance.SubjectType.PERSON, insured.name(), null, Map.of()));
        }
        return initial;
    }

    private boolean isHolderInsured(ProposalCreatedEvent event) {
        String holderCustomerId = event.insuredPartyList().holderInfo().customerId();
        return event.insuredPartyList().insuredList() != null
                && event.insuredPartyList().insuredList().stream()
                .anyMatch(insured -> holderCustomerId != null && holderCustomerId.equals(insured.customerId()));
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
            // 统一出单允许仅携带 customerId：客户主数据是身份真相，快照字段可在后续补录。
            if (!applicant.verifyApplicantInfo() && (applicant.applicantId() == null
                    || applicant.applicantId().isBlank())) {
                throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION",
                        "Invalid applicant info: " + applicant.name());
            }
        }
    }

    private void validateSubjects() {
        if (subjects.isEmpty()) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "At least one subject is required");
        }
        // 意向阶段只持有参与方摘要，物类团单的标的数量由后续投保险种段校验。
        // 这里仅要求至少一个意向标的，避免把单一投保人误判为单一财产标的。
    }

    private void ensureDraftStatus() {
        if (this.status.statusCode() != ProposalStatus.StatusCode.DRAFT) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "Only draft proposals can be modified");
        }
    }

    protected Proposal() {
    }
}
