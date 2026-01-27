package com.titanium.policy.aggregate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;

import com.titanium.policy.command.CreateProposalCommand;
import com.titanium.policy.command.SubmitProposalCommand;
import com.titanium.policy.entity.proposal.ProposalHolder;
import com.titanium.policy.entity.proposal.ProposalSubject;
import com.titanium.policy.valueobject.proposal.ProposalBasicInfo;
import com.titanium.policy.valueobject.proposal.ProposalStatus;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 投保意向单聚合根
 * <p>
 * 作为投保意向单聚合的唯一对外交互入口，协调内部组件动作，记录客户初步投保意愿
 * </p>
 */
@Aggregate
@Getter
@Builder(builderMethodName = "builder")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Proposal {
    /**
     * 聚合根唯一标识
     */
    @AggregateIdentifier
    private String                proposalId;
    /**
     * 意向单编号
     */
    private String                proposalNo;
    /**
     * 保单形态：个单/团单/父子
     */
    private String                policyForm;
    /**
     * 父意向单ID
     */
    private String                parentProposalId;
    /**
     * 销售渠道
     */
    private String                channel;
    /**
     * 创建时间
     */
    private LocalDateTime         createTime;
    /**
     * 更新时间
     */
    private LocalDateTime         updateTime;
    /**
     * 意向单基本信息
     */
    private ProposalBasicInfo     basicInfo;
    /**
     * 申请人列表
     */
    private List<ProposalHolder>  applicants;
    /**
     * 标的列表
     */
    private List<ProposalSubject> subjects;
    /**
     * 意向单状态
     */
    private ProposalStatus        status;
    /**
     * 租户ID
     */
    private String                tenantId;

    /**
     * 创建意向单 - 命令处理器
     * <p>
     * 处理创建意向单的命令
     * </p>
     *
     * @param command 创建意向单命令
     */
    @CommandHandler
    public Proposal(CreateProposalCommand command) {
        this.proposalId = command.proposalId();
        this.proposalNo = command.proposalNo();
        this.policyForm = command.policyForm();
        this.channel = command.channel();
        this.tenantId = command.tenantId();
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
        this.applicants = new ArrayList<>();
        this.subjects = new ArrayList<>();
        this.status = new ProposalStatus(ProposalStatus.StatusCode.DRAFT, LocalDateTime.now(), "创建草稿");

        // 从命令中导入基本信息
        this.basicInfo = new ProposalBasicInfo(command.customerId(), command.intendedSumInsured(),
                command.intendedPremium(), command.insurancePeriodStart(), command.insurancePeriodEnd(),
                command.expectedProductCode());
    }

    /**
     * 提交意向单 - 命令处理器
     * <p>
     * 处理提交意向单的命令
     * </p>
     *
     * @param command 提交意向单命令
     */
    @CommandHandler
    public void handle(SubmitProposalCommand command) {
        // 校验必填字段完整性
        validateRequiredFields();
        // 校验申请人信息
        validateApplicants();
        // 校验标的信息
        validateSubjects();
        // 更新状态为已提交
        this.status = this.status.transitionStatus(ProposalStatus.StatusCode.SUBMITTED, command.changeReason());
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 创建意向单草稿
     * <p>
     * 初始化基础信息，设置状态为草稿
     * </p>
     *
     * @param proposalId 意向单ID
     * @param proposalNo 意向单编号
     * @param policyForm 保单形态
     * @param channel 销售渠道
     * @param basicInfo 基本信息
     * @param tenantId 租户ID
     * @return 投保意向单聚合根实例
     */
    public static Proposal createDraft(String proposalId, String proposalNo, String policyForm, String channel,
                                       ProposalBasicInfo basicInfo, String tenantId) {
        Proposal proposal = new Proposal();
        proposal.proposalId = proposalId;
        proposal.proposalNo = proposalNo;
        proposal.policyForm = policyForm;
        proposal.channel = channel;
        proposal.basicInfo = basicInfo;
        proposal.tenantId = tenantId;
        proposal.createTime = LocalDateTime.now();
        proposal.updateTime = LocalDateTime.now();
        proposal.applicants = new ArrayList<>();
        proposal.subjects = new ArrayList<>();
        proposal.status = new ProposalStatus(ProposalStatus.StatusCode.DRAFT, LocalDateTime.now(), "创建草稿");
        return proposal;
    }

    /**
     * 提交意向单
     * <p>
     * 校验必填字段完整性，更新状态为已提交
     * </p>
     *
     * @param changeReason 提交原因
     */
    public void submitProposal(String changeReason) {
        // 校验必填字段完整性
        validateRequiredFields();
        // 校验申请人信息
        validateApplicants();
        // 校验标的信息
        validateSubjects();
        // 更新状态为已提交
        this.status = this.status.transitionStatus(ProposalStatus.StatusCode.SUBMITTED, changeReason);
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 转为投保单
     * <p>
     * 生成投保单初始化数据，更新状态为已转投保单
     * </p>
     *
     * @param changeReason 转换原因
     */
    public void convertToApplication(String changeReason) {
        // 只有已提交状态的意向单才能转为投保单
        if (this.status.statusCode() != ProposalStatus.StatusCode.SUBMITTED) {
            throw new IllegalArgumentException("Only submitted proposals can be converted to application");
        }
        // 更新状态为已转投保单
        this.status = this.status.transitionStatus(ProposalStatus.StatusCode.CONVERTED_TO_APPLICATION, changeReason);
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 作废意向单
     * <p>
     * 标记终止状态，更新状态为作废
     * </p>
     *
     * @param changeReason 作废原因
     */
    public void voidProposal(String changeReason) {
        // 只有草稿或已提交状态的意向单才能作废
        ProposalStatus.StatusCode currentStatus = this.status.statusCode();
        if (currentStatus != ProposalStatus.StatusCode.DRAFT && currentStatus != ProposalStatus.StatusCode.SUBMITTED) {
            throw new IllegalArgumentException("Only draft or submitted proposals can be voided");
        }
        // 更新状态为作废
        this.status = this.status.transitionStatus(ProposalStatus.StatusCode.VOIDED, changeReason);
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 新增意向标的
     *
     * @param subject 标的实体
     */
    public void addSubject(ProposalSubject subject) {
        // 只有草稿状态的意向单才能修改
        ensureDraftStatus();
        this.subjects.add(subject);
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 新增申请人
     *
     * @param applicant 申请人实体
     */
    public void addApplicant(ProposalHolder applicant) {
        // 只有草稿状态的意向单才能修改
        ensureDraftStatus();
        this.applicants.add(applicant);
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 校验必填字段完整性
     */
    private void validateRequiredFields() {
        // 基本信息不能为空
        if (basicInfo == null) {
            throw new IllegalArgumentException("Basic info cannot be null");
        }
        // 客户ID不能为空
        if (basicInfo.customerId() == null || basicInfo.customerId().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }
        // 意向险种编码不能为空
        if (basicInfo.expectedProductCode() == null || basicInfo.expectedProductCode().isEmpty()) {
            throw new IllegalArgumentException("Expected product code cannot be null or empty");
        }
    }

    /**
     * 校验申请人信息
     */
    private void validateApplicants() {
        if (applicants.isEmpty()) {
            throw new IllegalArgumentException("At least one applicant is required");
        }
        // 校验每个申请人信息的格式
        for (ProposalHolder applicant : applicants) {
            if (!applicant.verifyApplicantInfo()) {
                throw new IllegalArgumentException("Invalid applicant info: " + applicant.name());
            }
        }
    }

    /**
     * 校验标的信息
     */
    private void validateSubjects() {
        if (subjects.isEmpty()) {
            throw new IllegalArgumentException("At least one subject is required");
        }
        // 团单场景校验标的数量
        if ("GROUP".equals(this.policyForm) && subjects.size() < 2) {
            throw new IllegalArgumentException("Group policy requires at least 2 subjects");
        }
    }

    /**
     * 确保意向单处于草稿状态
     */
    private void ensureDraftStatus() {
        if (this.status.statusCode() != ProposalStatus.StatusCode.DRAFT) {
            throw new IllegalArgumentException("Only draft proposals can be modified");
        }
    }

    protected Proposal() {

    }
}
