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
import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.billing.PremiumCollectionMode;
import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.command.ConvertProposalToInsuranceCommand;
import com.titanium.policy.command.CreateInsuranceDirectlyCommand;
import com.titanium.policy.command.ReceiveUnderwritingResultCommand;
import com.titanium.policy.command.SubmitUnderwritingCommand;
import com.titanium.policy.command.TriggerIssuanceCommand;
import com.titanium.policy.common.enums.InsuranceStatusCode;
import com.titanium.policy.entity.insurance.InsuranceLine;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.event.insurance.InsuranceCreatedEvent;
import com.titanium.policy.event.insurance.InsuranceIssuedEvent;
import com.titanium.policy.event.insurance.InsuranceSubmittedForUnderwritingEvent;
import com.titanium.policy.event.insurance.UnderwritingResultReceivedEvent;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.valueobject.insurance.InsuranceBasicInfo;
import com.titanium.policy.valueobject.insurance.InsuranceStatus;
import com.titanium.policy.valueobject.insurance.UnderwritingResult;
import com.titanium.policy.valueobject.policy.ChannelInfo;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * 投保单聚合根
 * <p>
 * 作为投保单聚合的唯一对外交互入口，管理投保信息、核保对接、承保出单全流程。
 * </p>
 */
@Aggregate
@Getter
@SuperBuilder(toBuilder = true)
public class Insurance extends BaseAggregate {
    /** 聚合根唯一标识 */
    @AggregateIdentifier
    private String                 insuranceId;
    /** 投保单编号 */
    private String                 insuranceNo;
    /** 关联意向单ID */
    private String                 proposalId;
    /** 保单形态 */
    private PolicyForm             policyForm;
    /** 投保单基本信息 */
    private InsuranceBasicInfo     basicInfo;
    /**
     * 投保险种段列表（L2，一单多险的载体）
     * <p>
     * 取代原 {@code insuranceProducts}（仅编码+保额，且 {@code addInsuranceLine} 零调用方致恒空）
     * 与 {@code basicInfo.productCodeList}（裸编码列表）。每段独立持有保额/保费/期间/缴费/
     * 标的与<b>段级核保结论</b>——后者是「主险通过、附加险拒保」的建模基础。
     * </p>
     */
    private List<InsuranceLine>    insuranceLines;
    /** 投保参与方清单 */
    private InsuredPartyList       insuredPartyList;
    /**
     * 投保单级核保结果（整单结论）
     * <p>
     * 与段级结论并存而非重复：本字段表达「这张投保单整体能否承保」（由核保域出具的单据级结论），
     * 段级 {@code InsuranceLine.underwritingConclusion} 表达「每个险种各自的结论」。
     * 整单拒保时全部段拒保；整单通过时仍可能有个别段被拒。
     * </p>
     */
    private UnderwritingResult     underwritingResult;
    /** 投保单状态 */
    private InsuranceStatus        status;
    /** 险种三级分类（主险冗余，自意向单/直接创建透传，可空以兼容存量事件） */
    private InsuranceProductType   insuranceType;
    /** 收费方式（出单期确定，透传至保单） */
    private PremiumCollectionMode  collectionMode;
    /** 渠道信息（透传至保单） */
    private ChannelInfo            channelInfo;
    /** 出单业务流水号（幂等与进度追溯） */
    private String                 bizNo;
    /** 营销包ID（弱引用，可空） */
    private String                 marketPackageId;

    // ==================== CommandHandler ====================

    /**
     * 从意向单创建投保单（三步出单）
     */
    @CommandHandler
    public Insurance(ConvertProposalToInsuranceCommand command) {
        validateInsuranceLines(command.insuranceLines());
        AggregateLifecycle.apply(new InsuranceCreatedEvent(command.insuranceId(), command.insuranceNo(),
                command.proposalId(), command.policyForm(), command.applicantId(), command.insuredCount(),
                command.exactPremium() != null ? command.exactPremium().value() : null, command.insurancePeriodStart(),
                command.insurancePeriodEnd(), command.insuranceLines(), command.underwritingPriority(),
                command.insuredPartyList(), command.insuranceType(), command.collectionMode(), command.channelInfo(),
                command.bizNo(), command.marketPackageId(), LocalDateTime.now(), command.tenantId(),
                command.sumInsured(), command.paymentMode(), command.premiumPaymentYears()));
    }

    /**
     * 直接创建投保单（两步出单，跳过意向单）
     */
    @CommandHandler
    public Insurance(CreateInsuranceDirectlyCommand command) {
        validateInsuranceLines(command.insuranceLines());
        AggregateLifecycle.apply(new InsuranceCreatedEvent(command.insuranceId(), command.insuranceNo(), null,
                command.policyForm(), command.holderId(), command.insuredCount(), command.exactPremium(),
                command.insurancePeriodStart(), command.insurancePeriodEnd(), command.insuranceLines(),
                command.underwritingPriority(), command.insuredPartyList(), command.insuranceType(),
                command.collectionMode(), command.channelInfo(), command.bizNo(), command.marketPackageId(),
                LocalDateTime.now(), command.tenantId(), command.sumInsured(), command.paymentMode(),
                command.premiumPaymentYears()));
    }

    /**
     * 提交核保
     */
    @CommandHandler
    public void handle(SubmitUnderwritingCommand command) {
        // 校验状态
        InsuranceStatusCode currentStatus = this.status.statusCode();
        if (currentStatus != InsuranceStatusCode.DRAFT && currentStatus != InsuranceStatusCode.SUBMITTED
                && currentStatus != InsuranceStatusCode.UNDERWRITING_SUSPENDED) {
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
                this.basicInfo.exactPremium() != null ? this.basicInfo.exactPremium().currency() : CurrencyEnum.CNY.getCode(),
                this.basicInfo.insurancePeriodStart(), this.basicInfo.insurancePeriodEnd(), lineProductCodes(),
                this.basicInfo.underwritingPriority(), this.policyForm, this.tenantId, this.bizNo));
    }

    /**
     * 接收核保结果
     */
    @CommandHandler
    public void handle(ReceiveUnderwritingResultCommand command) {
        if (this.status.statusCode() != InsuranceStatusCode.UNDERWRITING) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION",
                    "Only applications in underwriting can receive results");
        }
        UnderwritingResult result = command.underwritingResult();
        AggregateLifecycle.apply(new UnderwritingResultReceivedEvent(this.insuranceId, result.underwritingId(),
                result.resultCode(), result.underwritingOpinion(), result.underwriterId(), result.underwritingTime(),
                result.condition(), this.tenantId, result.extraPremiumRatio(), this.bizNo));
    }

    /**
     * 触发承保出单
     */
    @CommandHandler
    public void handle(TriggerIssuanceCommand command) {
        if (this.status.statusCode() != InsuranceStatusCode.UNDERWRITING_APPROVED) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION",
                    "Only underwriting approved applications can trigger issuance");
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
        this.insuranceType = event.insuranceType();
        this.tenantId = event.tenantId();
        this.createTime = event.createTime();
        this.updateTime = event.createTime();
        // 险种段列表（L2）：事件携带则落地，缺失时置空列表（兼容存量事件；改造后出单链路必带）
        this.insuranceLines = event.insuranceLines() != null
                ? new ArrayList<>(event.insuranceLines())
                : new ArrayList<>();
        this.collectionMode = event.collectionMode();
        this.channelInfo = event.channelInfo();
        this.bizNo = event.bizNo();
        this.marketPackageId = event.marketPackageId();
        // 参与方清单从事件初始化；事件无清单时置 null，兼容存量事件（SubmitUnderwriting 有 null 校验保护）
        this.insuredPartyList = event.insuredPartyList();
        this.status = new InsuranceStatus(InsuranceStatusCode.DRAFT, event.createTime(),
                event.proposalId() != null ? "从意向单创建投保单" : "直接创建投保单");
        this.basicInfo = new InsuranceBasicInfo(event.holderId(), event.insuredCount(),
                event.exactPremium() != null ? Money.of(event.exactPremium(), CurrencyEnum.CNY.getCode()) : null,
                event.insurancePeriodStart(), event.insurancePeriodEnd(), event.productCodes(),
                event.underwritingPriority());
    }

    @EventSourcingHandler
    public void on(InsuranceSubmittedForUnderwritingEvent event) {
        this.status = this.status.transitionStatus(InsuranceStatusCode.UNDERWRITING, "提交核保");
        this.updateTime = LocalDateTime.now();
    }

    @EventSourcingHandler
    public void on(UnderwritingResultReceivedEvent event) {
        ConclusionType resultCode = event.resultCode();
        this.underwritingResult = new UnderwritingResult(event.underwritingId(), resultCode, event.opinion(),
                event.underwriterId(), event.underwritingTime(), event.underwritingCondition(),
                event.extraPremiumRatio());

        InsuranceStatusCode newStatus = switch (resultCode) {
            case ACCEPT, MODIFY -> InsuranceStatusCode.UNDERWRITING_APPROVED;
            case REJECT -> InsuranceStatusCode.UNDERWRITING_REJECTED;
            case POSTPONE -> InsuranceStatusCode.UNDERWRITING_SUSPENDED;
        };
        String changeReason = switch (resultCode) {
            case ACCEPT -> "核保通过";
            case MODIFY -> "核保通过（修改条件承保）";
            case REJECT -> "核保拒绝";
            case POSTPONE -> "核保暂缓";
        };
        this.status = this.status.transitionStatus(newStatus, changeReason);
        // 整单结论下发到各险种段：核保域当前出具单据级结论，段级差异化结论（主险通过/附加险拒保）
        // 待核保域支持分段核保后由 UpdateLineUnderwritingResultCommand 逐段覆盖。
        if (this.insuranceLines != null) {
            for (int i = 0; i < this.insuranceLines.size(); i++) {
                this.insuranceLines.set(i,
                        this.insuranceLines.get(i).withUnderwritingResult(resultCode, event.extraPremiumRatio()));
            }
        }
        this.updateTime = LocalDateTime.now();
    }

    @EventSourcingHandler
    public void on(InsuranceIssuedEvent event) {
        this.status = this.status.transitionStatus(InsuranceStatusCode.ISSUED, "触发承保流程");
        this.updateTime = event.issuedTime();
    }

    // ==================== 业务方法 ====================

    /**
     * 主险段（一张投保单有且仅有一个）。
     *
     * @return 主险段；无段时返回 null
     */
    public InsuranceLine mainLine() {
        if (this.insuranceLines == null) {
            return null;
        }
        return this.insuranceLines.stream().filter(InsuranceLine::isMain).findFirst().orElse(null);
    }

    /**
     * 附加险段列表。
     *
     * @return 附加险段列表
     */
    public List<InsuranceLine> riderLines() {
        if (this.insuranceLines == null) {
            return List.of();
        }
        return this.insuranceLines.stream().filter(InsuranceLine::isRider).toList();
    }

    /**
     * 按段ID查找险种段。
     *
     * @param lineId 段ID
     * @return 匹配的段；未找到返回 null
     */
    public InsuranceLine lineOf(String lineId) {
        if (this.insuranceLines == null || lineId == null) {
            return null;
        }
        return this.insuranceLines.stream()
                .filter(line -> lineId.equals(line.lineId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 投保单应付总保费（Σ 计入段的加费后保费；拒保段不计入）。
     * <p>
     * 核保加费按段计算后再汇总——不同险种加费率可不同（附加重疾加费 30%、主险不加费）。
     * </p>
     *
     * @return 应付总保费；无有效段时返回 null
     */
    public Money payableTotalPremium() {
        if (this.insuranceLines == null || this.insuranceLines.isEmpty()) {
            return this.basicInfo != null ? this.basicInfo.exactPremium() : null;
        }
        Money total = null;
        for (InsuranceLine line : this.insuranceLines) {
            Money linePremium = line.payablePremium();
            if (linePremium == null) {
                continue;
            }
            total = total == null ? linePremium : total.add(linePremium);
        }
        return total;
    }

    /**
     * 是否至少有一个险种段核保通过（整单可承保的前提——个别段拒保不阻断出单）。
     *
     * @return 存在可承保段返回 {@code true}
     */
    public boolean hasApprovedLine() {
        return this.insuranceLines != null
                && this.insuranceLines.stream().anyMatch(InsuranceLine::isUnderwritingApproved);
    }

    /**
     * 险种段的产品编码列表（供核保请求与保费计算等按编码消费的下游使用）。
     *
     * @return 产品编码列表；无段时回退基本信息中的编码列表
     */
    public List<String> lineProductCodes() {
        if (this.insuranceLines == null || this.insuranceLines.isEmpty()) {
            return this.basicInfo != null && this.basicInfo.productCodeList() != null
                    ? this.basicInfo.productCodeList()
                    : List.of();
        }
        return this.insuranceLines.stream().map(InsuranceLine::productCode).filter(code -> code != null).toList();
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

    /**
     * 校验险种段构成：至少一段、有且仅一个主险、附加险依附合法。
     * <p>
     * 段级不变量在提交核保前校验（核保按段出结论，段结构非法则核保无从进行）。
     * 投保要素的业务校验（年龄/保额/职业等依产品条件）由 application 层的
     * {@code IssuanceEligibilityDomainService} 在出单受理阶段完成，不在此重复。
     * </p>
     */
    private void validateInsuranceLines() {
        validateInsuranceLines(this.insuranceLines);
    }

    /**
     * 在首个事件写入前校验投保险种段，避免创建无法进入核保和承保流程的坏草稿。
     */
    private static void validateInsuranceLines(List<InsuranceLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "投保单至少须含一个险种段");
        }
        if (lines.stream().anyMatch(line -> line == null
                || line.productId() == null || line.productId().isBlank()
                || line.productCode() == null || line.productCode().isBlank())) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "投保险种段必须包含产品ID和产品编码");
        }
        long mainCount = lines.stream().filter(InsuranceLine::isMain).count();
        if (mainCount != 1) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION",
                    "投保单须含且仅含一个主险段，当前有 " + mainCount + " 个");
        }
        String mainLineId = lines.stream().filter(InsuranceLine::isMain).map(InsuranceLine::lineId).findFirst()
                .orElse(null);
        for (InsuranceLine line : lines) {
            if (line.isRider() && (line.parentLineId() == null || !line.parentLineId().equals(mainLineId))) {
                throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION",
                        "附加险段须依附于本单主险段: 段序号 " + line.lineNo());
            }
        }
    }

    private void ensureDraftStatus() {
        if (this.status.statusCode() != InsuranceStatusCode.DRAFT) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "Only draft applications can be modified");
        }
    }

    protected Insurance() {
    }
}
