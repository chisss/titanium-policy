package com.titanium.policy.aggregate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.policy.command.ConvertProposalToInsuranceCommand;
import com.titanium.policy.command.CreateInsuranceDirectlyCommand;
import com.titanium.policy.command.ReceiveUnderwritingResultCommand;
import com.titanium.policy.command.SubmitUnderwritingCommand;
import com.titanium.policy.command.TriggerIssuanceCommand;
import com.titanium.policy.entity.insurance.InsuranceProduct;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.event.insurance.InsuranceCreatedEvent;
import com.titanium.policy.event.insurance.InsuranceIssuedEvent;
import com.titanium.policy.event.insurance.InsuranceSubmittedForUnderwritingEvent;
import com.titanium.policy.event.insurance.UnderwritingResultReceivedEvent;
import com.titanium.policy.valueobject.Amount;
import com.titanium.policy.valueobject.insurance.InsuranceBasicInfo;
import com.titanium.policy.valueobject.insurance.InsuranceStatus;
import com.titanium.policy.valueobject.insurance.UnderwritingResult;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 投保单聚合根
 * <p>
 * 作为投保单聚合的唯一对外交互入口，管理投保信息、核保对接、承保出单全流程。
 * </p>
 */
@Aggregate
@Getter
@Builder(builderMethodName = "builder")
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Insurance {
    /** 聚合根唯一标识 */
    @AggregateIdentifier
    private String                 insuranceId;
    /** 投保单编号 */
    private String                 insuranceNo;
    /** 关联意向单ID */
    private String                 proposalId;
    /** 保单形态 */
    private PolicyForm             policyForm;
    /** 父投保单ID */
    private String                 parentInsuranceId;
    /** 创建时间 */
    private LocalDateTime          createTime;
    /** 更新时间 */
    private LocalDateTime          updateTime;
    /** 投保单基本信息 */
    private InsuranceBasicInfo     basicInfo;
    /** 投保险种列表 */
    private List<InsuranceProduct> insuranceProducts;
    /** 投保参与方清单 */
    private InsuredPartyList       insuredPartyList;
    /** 核保结果 */
    private UnderwritingResult     underwritingResult;
    /** 投保单状态 */
    private InsuranceStatus        status;
    /** 租户ID */
    private String                 tenantId;

    // ==================== CommandHandler ====================

    /**
     * 从意向单创建投保单（三步出单）
     */
    @CommandHandler
    public Insurance(ConvertProposalToInsuranceCommand command) {
        AggregateLifecycle.apply(new InsuranceCreatedEvent(command.insuranceId(), command.insuranceNo(),
                command.proposalId(), command.policyForm(), command.applicantId(), command.insuredCount(),
                command.exactPremium() != null ? command.exactPremium().value() : null, command.insurancePeriodStart(),
                command.insurancePeriodEnd(), command.productCodes(), command.underwritingPriority(),
                LocalDateTime.now(), command.tenantId()));
    }

    /**
     * 直接创建投保单（两步出单，跳过意向单）
     */
    @CommandHandler
    public Insurance(CreateInsuranceDirectlyCommand command) {
        AggregateLifecycle.apply(new InsuranceCreatedEvent(command.insuranceId(), command.insuranceNo(), null, // 无关联意向单
                command.policyForm(), command.holderId(), command.insuredCount(), command.exactPremium(),
                command.insurancePeriodStart(), command.insurancePeriodEnd(), command.productCodes(),
                command.underwritingPriority(), LocalDateTime.now(), command.tenantId()));
    }

    /**
     * 提交核保
     */
    @CommandHandler
    public void handle(SubmitUnderwritingCommand command) {
        // 校验状态
        InsuranceStatus.StatusCode currentStatus = this.status.statusCode();
        if (currentStatus != InsuranceStatus.StatusCode.DRAFT && currentStatus != InsuranceStatus.StatusCode.SUBMITTED
                && currentStatus != InsuranceStatus.StatusCode.UNDERWRITING_SUSPENDED) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION",
                    "Only draft, submitted or suspended applications can be submitted for underwriting");
        }
        validateApplicationInfo();
        if (this.insuredPartyList != null && !this.insuredPartyList.verifyPartyInfo()) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "Invalid insured party information");
        }
        validateInsuranceLines();

        AggregateLifecycle.apply(new InsuranceSubmittedForUnderwritingEvent(this.insuranceId, this.insuranceNo,
                this.basicInfo.holderId(), this.basicInfo.insuredCount(),
                this.basicInfo.exactPremium() != null ? this.basicInfo.exactPremium().value() : null,
                this.basicInfo.exactPremium() != null ? this.basicInfo.exactPremium().currency() : "CNY",
                this.basicInfo.insurancePeriodStart(), this.basicInfo.insurancePeriodEnd(),
                this.basicInfo.productCodeList(), this.basicInfo.underwritingPriority(), this.policyForm,
                this.tenantId));
    }

    /**
     * 接收核保结果
     */
    @CommandHandler
    public void handle(ReceiveUnderwritingResultCommand command) {
        if (this.status.statusCode() != InsuranceStatus.StatusCode.UNDERWRITING) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "Only applications in underwriting can receive results");
        }
        UnderwritingResult result = command.underwritingResult();
        AggregateLifecycle.apply(new UnderwritingResultReceivedEvent(this.insuranceId, result.underwritingId(),
                result.resultCode(), result.underwritingOpinion(), result.underwriterId(),
                result.underwritingTime(), result.condition(), this.tenantId));
    }

    /**
     * 触发承保出单
     */
    @CommandHandler
    public void handle(TriggerIssuanceCommand command) {
        if (this.status.statusCode() != InsuranceStatus.StatusCode.UNDERWRITING_APPROVED) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "Only underwriting approved applications can trigger issuance");
        }
        AggregateLifecycle.apply(
                new InsuranceIssuedEvent(this.insuranceId, this.insuranceNo, LocalDateTime.now(), this.tenantId));
    }

    // ==================== EventSourcingHandler ====================

    @EventSourcingHandler
    public void on(InsuranceCreatedEvent event) {
        this.insuranceId = event.insuranceId();
        this.insuranceNo = event.insuranceNo();
        this.proposalId = event.proposalId();
        this.policyForm = event.policyForm();
        this.tenantId = event.tenantId();
        this.createTime = event.createTime();
        this.updateTime = event.createTime();
        this.insuranceProducts = new ArrayList<>();
        this.status = new InsuranceStatus(InsuranceStatus.StatusCode.DRAFT, event.createTime(),
                event.proposalId() != null ? "从意向单创建投保单" : "直接创建投保单");
        this.basicInfo = new InsuranceBasicInfo(event.holderId(), event.insuredCount(),
                event.exactPremium() != null ? Amount.of(event.exactPremium(), "CNY") : null,
                event.insurancePeriodStart(), event.insurancePeriodEnd(), event.productCodes(),
                event.underwritingPriority());
    }

    @EventSourcingHandler
    public void on(InsuranceSubmittedForUnderwritingEvent event) {
        this.status = this.status.transitionStatus(InsuranceStatus.StatusCode.UNDERWRITING, "提交核保");
        this.updateTime = LocalDateTime.now();
    }

    @EventSourcingHandler
    public void on(UnderwritingResultReceivedEvent event) {
        ConclusionType resultCode = event.resultCode();
        this.underwritingResult = new UnderwritingResult(event.underwritingId(), resultCode, event.opinion(),
                event.underwriterId(), event.underwritingTime(), event.underwritingCondition());

        InsuranceStatus.StatusCode newStatus = switch (resultCode) {
            case ACCEPT, MODIFY -> InsuranceStatus.StatusCode.UNDERWRITING_APPROVED;
            case REJECT -> InsuranceStatus.StatusCode.UNDERWRITING_REJECTED;
            case POSTPONE -> InsuranceStatus.StatusCode.UNDERWRITING_SUSPENDED;
        };
        String changeReason = switch (resultCode) {
            case ACCEPT -> "核保通过";
            case MODIFY -> "核保通过（修改条件承保）";
            case REJECT -> "核保拒绝";
            case POSTPONE -> "核保暂缓";
        };
        this.status = this.status.transitionStatus(newStatus, changeReason);
        this.updateTime = LocalDateTime.now();
    }

    @EventSourcingHandler
    public void on(InsuranceIssuedEvent event) {
        this.status = this.status.transitionStatus(InsuranceStatus.StatusCode.ISSUED, "触发承保流程");
        this.updateTime = event.issuedTime();
    }

    // ==================== 业务方法 ====================

    /**
     * 新增投保险种
     */
    public void addInsuranceLine(InsuranceProduct insuranceProduct) {
        ensureDraftStatus();
        if (!insuranceProduct.verifyLineConstraint()) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "Invalid insurance line constraint: " + insuranceProduct.productCode());
        }
        this.insuranceProducts.add(insuranceProduct);
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 设置投保参与方清单
     */
    public void setInsuredPartyList(InsuredPartyList insuredPartyList) {
        ensureDraftStatus();
        this.insuredPartyList = insuredPartyList;
        this.updateTime = LocalDateTime.now();
    }

    // ==================== 校验方法 ====================

    private void validateApplicationInfo() {
        if (this.basicInfo == null) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "Basic info cannot be null");
        }
        if (this.basicInfo.holderId() == null || this.basicInfo.holderId().isEmpty()) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "Holder ID cannot be null or empty");
        }
    }

    private void validateInsuranceLines() {
        if (this.insuranceProducts == null || this.insuranceProducts.isEmpty()) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "At least one insurance line is required");
        }
        for (InsuranceProduct line : this.insuranceProducts) {
            if (!line.verifyLineConstraint()) {
                throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "Invalid insurance line: " + line.productCode());
            }
        }
    }

    private void ensureDraftStatus() {
        if (this.status.statusCode() != InsuranceStatus.StatusCode.DRAFT) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "Only draft applications can be modified");
        }
    }

    protected Insurance() {
    }
}
