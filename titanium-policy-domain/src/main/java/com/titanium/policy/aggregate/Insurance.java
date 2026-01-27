package com.titanium.policy.aggregate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;

import com.titanium.policy.command.ConvertProposalToInsuranceCommand;
import com.titanium.policy.entity.insurance.InsuranceProduct;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.valueobject.insurance.InsuranceBasicInfo;
import com.titanium.policy.valueobject.insurance.InsuranceStatus;
import com.titanium.policy.valueobject.insurance.UnderwritingResult;

import lombok.Getter;

/**
 * 投保单聚合根
 * <p>
 * 作为投保单聚合的唯一对外交互入口，协调内部组件动作，记录完整正式投保信息
 * </p>
 */
@Aggregate
@Getter
@Builder(builderMethodName = "builder")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Insurance {
    /**
     * 聚合根唯一标识
     */
    @AggregateIdentifier
    private String                 insuranceId;
    /**
     * 投保单编号
     */
    private String                 insuranceNo;
    /**
     * 关联意向单ID
     */
    private String                 proposalId;
    /**
     * 保单形态
     */
    private String                 policyForm;
    /**
     * 父投保单ID
     */
    private String                 parentInsuranceId;
    /**
     * 创建时间
     */
    private LocalDateTime          createTime;
    /**
     * 更新时间
     */
    private LocalDateTime          updateTime;
    /**
     * 投保单基本信息
     */
    private InsuranceBasicInfo basicInfo;
    /**
     * 投保险种列表
     */
    private List<InsuranceProduct> insuranceProducts;
    /**
     * 投保参与方清单
     */
    private InsuredPartyList       insuredPartyList;
    /**
     * 核保结果
     */
    private UnderwritingResult     underwritingResult;
    /**
     * 投保单状态
     */
    private InsuranceStatus status;
    /**
     * 租户ID
     */
    private String                 tenantId;

    /**
     * 从意向单创建投保单 - 命令处理器
     * <p>
     * 处理从意向单转换为投保单的命令
     * </p>
     *
     * @param command 转换意向单为投保单命令
     */
    @CommandHandler
    public Insurance(ConvertProposalToInsuranceCommand command) {
        this.insuranceId = command.insuranceId();
        this.insuranceNo = command.insuranceNo();
        this.proposalId = command.proposalId();
        this.policyForm = command.policyForm();
        this.tenantId = command.tenantId();
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
        this.insuranceProducts = new ArrayList<>();
        this.status = new InsuranceStatus(InsuranceStatus.StatusCode.DRAFT, LocalDateTime.now(), "从意向单创建投保单");

        // 从命令中导入基本信息
        this.basicInfo = new InsuranceBasicInfo(command.applicantId(), command.insuredCount(), command.exactPremium(),
                command.insurancePeriodStart(), command.insurancePeriodEnd(), command.productCodes(),
                command.underwritingPriority());
    }

    /**
     * 从意向单创建投保单
     * <p>
     * 从意向单导入数据创建投保单
     * </p>
     *
     * @param insuranceId 投保单ID
     * @param insuranceNo 投保单编号
     * @param proposal 投保意向单
     * @param tenantId 租户ID
     * @return 投保单聚合根实例
     */
    public static Insurance createFromProposal(String insuranceId, String insuranceNo, Proposal proposal,
                                               String tenantId) {
        Insurance application = new Insurance();
        application.insuranceId = insuranceId;
        application.insuranceNo = insuranceNo;
        application.proposalId = proposal.getProposalId();
        application.policyForm = proposal.getPolicyForm();
        application.tenantId = tenantId;
        application.createTime = LocalDateTime.now();
        application.updateTime = LocalDateTime.now();
        application.insuranceProducts = new ArrayList<>();
        application.status = new InsuranceStatus(InsuranceStatus.StatusCode.DRAFT, LocalDateTime.now(),
                "从意向单创建投保单");

        // 从意向单导入基本信息
        application.basicInfo = new InsuranceBasicInfo(proposal.getBasicInfo().customerId(), 0, // 被保险人数量
                proposal.getBasicInfo().intendedPremium(), proposal.getBasicInfo().insurancePeriodStart(),
                proposal.getBasicInfo().insurancePeriodEnd(), List.of(proposal.getBasicInfo().expectedProductCode()), 0 // 核保优先级
        );

        return application;
    }

    /**
     * 提交核保
     * <p>
     * 提交核保，封装数据调用核保域
     */
    public void submitUnderwriting() {
        // 校验投保单状态
        if (this.status.statusCode() != InsuranceStatus.StatusCode.DRAFT
                && this.status.statusCode() != InsuranceStatus.StatusCode.SUBMITTED
                && this.status.statusCode() != InsuranceStatus.StatusCode.UNDERWRITING_SUSPENDED) {
            throw new IllegalArgumentException(
                    "Only draft, submitted or suspended applications can be submitted for underwriting");
        }

        // 校验投保信息完整性
        validateApplicationInfo();

        // 校验参与方身份
        if (!this.insuredPartyList.verifyPartyInfo()) {
            throw new IllegalArgumentException("Invalid insured party information");
        }

        // 校验险种约束
        validateInsuranceLines();

        // 校验标的信息
        validateSubjectInfo();

        // 更新状态为核保中
        this.status = this.status.transitionStatus(InsuranceStatus.StatusCode.UNDERWRITING, "提交核保");
        this.updateTime = LocalDateTime.now();

        // 这里应该调用核保域的服务
        // 暂时省略核保调用逻辑
    }

    /**
     * 接收核保结果
     * <p>
     * 接收核保结果，更新内部状态
     * </p>
     *
     * @param underwritingResult 核保结果
     */
    public void receiveUnderwritingResult(UnderwritingResult underwritingResult) {
        // 校验投保单状态
        if (this.status.statusCode() != InsuranceStatus.StatusCode.UNDERWRITING) {
            throw new IllegalArgumentException("Only applications in underwriting can receive underwriting results");
        }

        // 更新核保结果
        this.underwritingResult = underwritingResult;
        this.updateTime = LocalDateTime.now();

        // 根据核保结果更新投保单状态
        InsuranceStatus.StatusCode newStatus;
        String changeReason;

        switch (underwritingResult.resultCode()) {
            case APPROVED -> {
                newStatus = InsuranceStatus.StatusCode.UNDERWRITING_APPROVED;
                changeReason = "核保通过";
            }
            case REJECTED -> {
                newStatus = InsuranceStatus.StatusCode.UNDERWRITING_REJECTED;
                changeReason = "核保拒绝";
            }
            case SUSPENDED -> {
                newStatus = InsuranceStatus.StatusCode.UNDERWRITING_SUSPENDED;
                changeReason = "核保暂缓";
            }
            default -> throw new IllegalArgumentException(
                    "Unknown underwriting result code: " + underwritingResult.resultCode());
        }

        this.status = this.status.transitionStatus(newStatus, changeReason);
    }

    /**
     * 触发承保流程
     * <p>
     * 核保通过后触发承保流程
     * </p>
     */
    public void triggerUnderwriting() {
        // 只有核保通过的投保单才能触发承保流程
        if (this.status.statusCode() != InsuranceStatus.StatusCode.UNDERWRITING_APPROVED) {
            throw new IllegalArgumentException("Only underwriting approved applications can trigger underwriting");
        }

        // 这里应该调用承保域的服务
        // 暂时省略承保调用逻辑

        // 更新状态为已承保
        this.status = this.status.transitionStatus(InsuranceStatus.StatusCode.ISSUED, "触发承保流程");
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 新增投保险种
     * <p>
     * 新增投保险种，校验险种约束
     * </p>
     *
     * @param insuranceProduct 投保险种
     */
    public void addInsuranceLine(InsuranceProduct insuranceProduct) {
        // 只有草稿状态的投保单才能修改
        ensureDraftStatus();

        // 校验险种约束
        if (!insuranceProduct.verifyLineConstraint()) {
            throw new IllegalArgumentException("Invalid insurance line constraint: " + insuranceProduct.productCode());
        }

        this.insuranceProducts.add(insuranceProduct);
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 设置投保参与方清单
     * <p>
     * 设置投保参与方清单，校验参与方信息
     * </p>
     *
     * @param insuredPartyList 投保参与方清单
     */
    public void setInsuredPartyList(InsuredPartyList insuredPartyList) {
        // 只有草稿状态的投保单才能修改
        ensureDraftStatus();

        this.insuredPartyList = insuredPartyList;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 校验投保信息完整性
     */
    private void validateApplicationInfo() {
        // 基本信息不能为空
        if (this.basicInfo == null) {
            throw new IllegalArgumentException("Basic info cannot be null");
        }
        // 投保人ID不能为空
        if (this.basicInfo.holderId() == null || this.basicInfo.holderId().isEmpty()) {
            throw new IllegalArgumentException("Holder ID cannot be null or empty");
        }
    }

    /**
     * 校验险种约束
     */
    private void validateInsuranceLines() {
        // 至少有一个险种
        if (this.insuranceProducts == null || this.insuranceProducts.isEmpty()) {
            throw new IllegalArgumentException("At least one insurance line is required");
        }
        // 校验每个险种的约束
        for (InsuranceProduct line : this.insuranceProducts) {
            if (!line.verifyLineConstraint()) {
                throw new IllegalArgumentException("Invalid insurance line: " + line.productCode());
            }
        }
    }

    /**
     * 校验标的信息
     */
    private void validateSubjectInfo() {
        // 这里应该根据实际业务逻辑校验标的信息
        // 暂时省略
    }

    /**
     * 确保投保单处于草稿状态
     */
    private void ensureDraftStatus() {
        if (this.status.statusCode() != InsuranceStatus.StatusCode.DRAFT) {
            throw new IllegalArgumentException("Only draft applications can be modified");
        }
    }

    protected Insurance() {
        // 默认构造函数，用于序列化
    }
}
