package com.titanium.policy.aggregate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.titanium.common.domain.BaseAggregate;
import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.maintenance.PolicyMaintenanceAction;
import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.command.ActivatePolicyCommand;
import com.titanium.policy.command.AddInsuredMemberCommand;
import com.titanium.policy.command.ApplyPolicyEndorsementCommand;
import com.titanium.policy.command.ApplyPolicyMaintenanceCommand;
import com.titanium.policy.command.AssociatePremiumBillingCommand;
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
import com.titanium.policy.command.RecordPremiumCollectionCommand;
import com.titanium.policy.command.ReinstatePolicyCommand;
import com.titanium.policy.command.RemoveInsuredMemberCommand;
import com.titanium.policy.command.ResumePolicyCommand;
import com.titanium.policy.command.StartAnnuityPayoutCommand;
import com.titanium.policy.command.SuspendPolicyCommand;
import com.titanium.policy.command.TerminatePolicyCommand;
import com.titanium.policy.command.UpdateAccountValueCommand;
import com.titanium.policy.command.UpdateLineUnderwritingResultCommand;
import com.titanium.policy.command.WaivePremiumCommand;
import com.titanium.policy.common.constant.PolicyConstants;
import com.titanium.policy.common.enums.PolicyDataUpdateType;
import com.titanium.policy.common.enums.PremiumWaiverReason;
import com.titanium.policy.entity.Endorsement;
import com.titanium.policy.entity.PaymentRecord;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.event.AccountValueUpdatedEvent;
import com.titanium.policy.event.AnnuityBenefitPaidEvent;
import com.titanium.policy.event.AnnuityPayoutStartedEvent;
import com.titanium.policy.event.DividendDistributedEvent;
import com.titanium.policy.event.InsuredMemberAddedEvent;
import com.titanium.policy.event.InsuredMemberRemovedEvent;
import com.titanium.policy.event.InvestmentAccountLinkedEvent;
import com.titanium.policy.event.LineUnderwritingResultUpdatedEvent;
import com.titanium.policy.event.PolicyActivatedEvent;
import com.titanium.policy.event.PolicyCancelledEvent;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.event.PolicyEndorsedEvent;
import com.titanium.policy.event.PolicyExpiredEvent;
import com.titanium.policy.event.PolicyIssuedEvent;
import com.titanium.policy.event.PolicyLapsedEvent;
import com.titanium.policy.event.PolicyMaintenanceAppliedEvent;
import com.titanium.policy.event.PolicyMaintenanceRetroactiveEvidenceRecordedEvent;
import com.titanium.policy.event.PolicyMaintenanceStateAppliedEvent;
import com.titanium.policy.event.PolicyMaturedEvent;
import com.titanium.policy.event.PolicyPaymentRecordedEvent;
import com.titanium.policy.event.PolicyReinstatedEvent;
import com.titanium.policy.event.PolicyResumedEvent;
import com.titanium.policy.event.PolicySuspendedEvent;
import com.titanium.policy.event.PolicyTerminatedEvent;
import com.titanium.policy.event.PremiumBillingAssociatedEvent;
import com.titanium.policy.event.PremiumCollectedEvent;
import com.titanium.policy.event.PremiumWaivedEvent;
import com.titanium.policy.event.SubPolicyLinkedEvent;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.service.PolicyCompositionDomainService;
import com.titanium.policy.service.maintenance.PolicyMaintenanceFieldExecutorRegistry;
import com.titanium.policy.service.maintenance.PolicyMaintenanceHashing;
import com.titanium.policy.valueobject.AnnuityPayoutPlan;
import com.titanium.policy.valueobject.PolicyDocument;
import com.titanium.policy.valueobject.PolicyNo;
import com.titanium.policy.valueobject.PolicyRelation;
import com.titanium.policy.valueobject.PolicyStatus;
import com.titanium.policy.valueobject.PremiumPlan;
import com.titanium.policy.valueobject.RuleDecision;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceApplicationReceipt;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceExecutionState;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceFieldChange;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceSnapshotFieldValue;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceSnapshotReference;
import com.titanium.policy.valueobject.policy.ChannelInfo;
import com.titanium.policy.valueobject.policy.CollectionInfo;
import com.titanium.policy.valueobject.policy.PolicyPeriod;

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
    private static final Set<String> SUPPORTED_MAINTENANCE_EFFECTIVE_TIME_TYPES = Set.of(
            "IMMEDIATE", "FUTURE", "SPECIFIED_DATE", "NEXT_BILLING_DATE", "POLICY_ANNIVERSARY",
            "RETROACTIVE");

    /** 聚合根唯一标识 */
    @AggregateIdentifier
    private String                 policyId;
    /** 保单号，对外唯一 */
    private PolicyNo               policyNo;
    /** 关联投保单ID */
    private String                 insuranceId;
    /** 保单形态 */
    private PolicyForm             policyForm;
    /** 签发时间 */
    private LocalDateTime          issueTime;
    /** 关联意向单ID（三步出单来源；三级贯通 Proposal→Insurance→Policy 的回指） */
    private String                 proposalId;
    /** 关联核保单ID（承保依据溯源） */
    private String                 underwritingId;
    /** 营销包ID（弱引用 marketing 域，仅溯源与转化统计；无营销来源时为 null） */
    private String                 marketPackageId;
    /** 出单业务流水号（进度回写关联键，可空以兼容存量保单） */
    private String                 bizNo;
    /**
     * 保单总保费（= Σ 计入段的保费；拒保段不计入）
     * <p>
     * 取代原 {@code PolicyBasicInfo.totalPremium}。原 {@code basicInfo} 的 7 个字段中，
     * 投保人ID/被保险人数已由 {@code insuredPartyList} 承载、保障起止期已由 {@code policyPeriod}
     * 承载、销售渠道已由 {@code channelInfo} 承载，仅总保费与版本号是唯一真相，故拆为直属字段
     * 并删除该值对象，消除「同一事实两处存放」的漂移风险。
     * </p>
     */
    private Money                  totalPremium;
    /** 保单业务版本号（批改后递增；区别于 Axon 聚合序列号） */
    private int                    policyVersion;
    /** 保单关系 */
    private PolicyRelation         policyRelation;
    /**
     * 险种段列表（L2，一单多险的载体）
     * <p>
     * 一张保单含 1..N 个险种段，每段对应一个产品并持有各自的保额/保费/保障期间/缴费条件/
     * 核保结论/承保状态，段内含条款快照（L2.5）、标的（L3）、责任快照（L4）。
     * 单险种保单即长度为 1。取代原 {@code insuranceProducts} + {@code subjects} 两个恒空列表。
     * </p>
     */
    private List<PolicyProduct>    policyProducts;
    /** 保单期间（保障期 + 等待期 + 犹豫期） */
    private PolicyPeriod           policyPeriod;
    /** 收费信息（收费方式/账单/支付单/应收实收/收讫状态） */
    private CollectionInfo         collectionInfo;
    /** 渠道信息（来源渠道/销售渠道大类/代理人） */
    private ChannelInfo            channelInfo;
    /** 保费计划 */
    private PremiumPlan            premiumPlan;
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
    /** 正式保全应用回执（按请求ID幂等恢复）。 */
    private List<PolicyMaintenanceApplicationReceipt> maintenanceApplications;
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
     * 创建保单（承保出单：从投保单 + 核保结果创建）
     * <p>
     * 险种段构成的四条不变量（唯一主险 / 附加险依附合法 / 保费守恒 / 段标识唯一）由
     * {@link PolicyCompositionDomainService} 裁决——该服务为纯领域服务（无 Port、无 CommandGateway），
     * 经 Axon 的 {@code @CommandHandler} 参数注入取得，属合法用法（入参仍为实体与值对象）。
     * </p>
     *
     * @param command                 创建保单命令
     * @param compositionDomainService 保单构成领域服务（Axon 参数注入）
     */
    @CommandHandler
    public Policy(CreatePolicyCommand command, PolicyCompositionDomainService compositionDomainService) {
        validateComposition(compositionDomainService, command.policyProducts(), command.premium());
        AggregateLifecycle.apply(new PolicyCreatedEvent(command.policyId(), new PolicyNo(command.policyNo()),
                command.policyForm(), command.productId(), command.insuranceId(), command.proposalId(),
                command.underwritingId(), command.bizNo(), command.marketPackageId(), command.policyPeriod(),
                command.standardPremium(), command.premium(), command.sumInsured(), command.policyProducts(),
                command.premiumPlan(), command.collectionInfo(),
                command.channelInfo(),
                new PolicyStatus(PolicyStatus.StatusCode.NOT_EFFECTIVE, LocalDateTime.now(), "创建保单",
                        PolicyConstants.POLICY_SYSTEM),
                command.insuredPartyList(), command.insuranceType(), command.tenantId()));
    }

    /**
     * 一步出单直接创建保单（免核保短险，录入即出单）
     * <p>
     * 与承保出单产出结构一致：同样落地险种段、参与方清单、期间、缴费与收费信息，下游读侧与
     * 理赔无需区分出单模式。改造前本路径丢弃参与方（传 null），已修复。
     * </p>
     *
     * @param command                 一步出单命令
     * @param compositionDomainService 保单构成领域服务（Axon 参数注入）
     */
    @CommandHandler
    public Policy(CreatePolicyDirectlyCommand command, PolicyCompositionDomainService compositionDomainService) {
        validateComposition(compositionDomainService, command.policyProducts(), command.totalPremium());
        AggregateLifecycle.apply(new PolicyCreatedEvent(command.policyId(), new PolicyNo(command.policyNo()),
                command.policyForm(), command.productId(), null, null, null, command.bizNo(), command.marketPackageId(),
                command.policyPeriod(), command.totalPremium(), command.totalPremium(), command.sumInsured(),
                command.policyProducts(), command.premiumPlan(), command.collectionInfo(), command.channelInfo(),
                new PolicyStatus(PolicyStatus.StatusCode.NOT_EFFECTIVE, LocalDateTime.now(), "一步出单创建保单",
                        PolicyConstants.POLICY_SYSTEM),
                command.insuredPartyList(), command.insuranceType(), command.tenantId()));
    }

    /**
     * 校验险种段构成，不通过则转译为业务异常（不变量守护）。
     * <p>
     * 段列表为空时放行：兼容存量单险种链路尚未改造完成的调用方（其险种信息在读侧冗余字段），
     * 出单入口贯通后由应用层保证段列表非空。
     * </p>
     */
    private void validateComposition(PolicyCompositionDomainService compositionDomainService,
                                     List<PolicyProduct> lines, Money totalPremium) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        RuleDecision decision = compositionDomainService.validate(lines, totalPremium);
        if (!decision.passed()) {
            throw new PolicyBusinessRuleException("POLICY_COMPOSITION_INVALID", decision.defaultMessage());
        }
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
        Money issuedPremium = totalPremium();
        AggregateLifecycle.apply(new PolicyIssuedEvent(this.policyId, this.policyNo.value(), this.productId,
                issuedPremium, this.sumInsured, LocalDateTime.now(), command.operatorId(), this.tenantId));
    }

    /**
     * 激活保单（生效）
     * <p>
     * 🔴 <b>收费校验修正</b>：改造前依赖 {@code premiumPlan.paymentStatus()}，而 {@code premiumPlan}
     * 在事件溯源后恒为 null，使该校验被整体短路——「未收费也能激活保单」。现改为依
     * {@link CollectionInfo#allowsActivation()} 判定：已收讫、或收费方式本身不以收讫为生效前提
     * （先享后付）方可生效。
     * </p>
     */
    @CommandHandler
    public void handle(ActivatePolicyCommand command) {
        if (this.status.statusCode() != PolicyStatus.StatusCode.NOT_EFFECTIVE) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION",
                    "Only NOT_EFFECTIVE policies can be activated");
        }
        if (!isPremiumConditionSatisfied()) {
            throw new PolicyBusinessRuleException("POLICY_PREMIUM_NOT_COLLECTED",
                    "首期保费未收讫，保单不可生效（收费方式："
                            + (this.collectionInfo != null && this.collectionInfo.collectionMode() != null
                                    ? this.collectionInfo.collectionMode().getName()
                                    : "未知")
                            + "）");
        }
        // 校验保障起期是否到达
        if (this.policyPeriod != null && !this.policyPeriod.hasStarted(LocalDateTime.now())) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "Insurance period has not started yet");
        }
        AggregateLifecycle.apply(new PolicyActivatedEvent(this.policyId, this.insuranceId, this.bizNo,
                LocalDateTime.now(), this.tenantId));
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
        int versionAfter = this.policyVersion + 1;
        AggregateLifecycle.apply(new PolicyEndorsedEvent(this.policyId, command.endorsementNo(), command.updateType(),
                command.updateType().getCategory(), versionAfter, command.endorsementEffectiveDate(),
                command.changeSummary(), command.originalSnapshot(), command.updateType().needsPremiumRecalc(),
                command.sourceMaintenanceId(), LocalDateTime.now(), command.operatorId(), this.tenantId));
    }

    /** 立即原子应用保全案件的结构化字段与合同状态动作。 */
    @CommandHandler
    public PolicyMaintenanceApplicationReceipt handle(
            ApplyPolicyMaintenanceCommand command,
            PolicyMaintenanceFieldExecutorRegistry executorRegistry) {
        validateMaintenanceRequestIdentity(command);
        PolicyMaintenanceApplicationReceipt existing = findMaintenanceApplication(command.requestId());
        if (existing != null) {
            if (existing.requestPayloadHash().equals(command.requestPayloadHash())
                    && existing.expectedPolicyVersion() == command.expectedPolicyVersion()) {
                return existing;
            }
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_IDEMPOTENCY_CONFLICT", "同一保全请求ID不能提交不同载荷");
        }
        validateNewMaintenanceRequest(command);
        if (this.policyVersion != command.expectedPolicyVersion()) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_VERSION_CONFLICT",
                    "Policy 版本冲突，期望 " + command.expectedPolicyVersion() + "，实际 " + this.policyVersion);
        }
        String calculatedRequestHash = PolicyMaintenanceHashing.requestHash(
                command.tenantId(), command.policyId(), command.requestId(), command.sourceMaintenanceId(),
                command.expectedPolicyVersion(), command.proposedSnapshotHash(), command.effectiveTimeType(),
                command.effectiveAt(), command.changeSummary(), command.changes(), command.stateAction(),
                command.stateReason(), command.terminationReason(), command.retroactiveEvidence());
        if (!calculatedRequestHash.equalsIgnoreCase(command.requestPayloadHash())) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_REQUEST_HASH_MISMATCH", "保全请求摘要与结构化载荷不一致");
        }

        PolicyMaintenanceExecutionState beforeState =
                new PolicyMaintenanceExecutionState(this.insuredPartyList, this.policyProducts);
        PolicyMaintenanceFieldExecutorRegistry.ExecutionResult execution = command.changes().isEmpty()
                ? new PolicyMaintenanceFieldExecutorRegistry.ExecutionResult(beforeState, List.of())
                : executorRegistry.execute(this.policyId, beforeState, command.changes());
        PolicyStatus.StatusCode statusBefore = this.status.statusCode();
        PolicyStatus.StatusCode statusAfter = maintenanceStatusAfter(command.stateAction(), statusBefore);
        int actualVersion = this.policyVersion + 1;
        LocalDateTime appliedAt = LocalDateTime.now();
        String endorsementNo = PolicyMaintenanceHashing.stableEndorsementNo(
                this.tenantId, this.policyId, command.requestId());
        String originalSnapshotHash = maintenanceSnapshotHash(
                this.policyVersion, beforeState, statusBefore);
        String appliedSnapshotHash = maintenanceSnapshotHash(
                actualVersion, execution.state(), statusAfter);
        String snapshotStorageKey = "axon-event://policy/" + this.tenantId + "/" + this.policyId
                + "/maintenance-applications/" + command.requestId() + "?version=" + actualVersion;
        String applicationHash = PolicyMaintenanceHashing.applicationHash(
                command.requestId(), endorsementNo, command.expectedPolicyVersion(), actualVersion,
                appliedSnapshotHash, execution.appliedFields());
        if (command.stateAction().changesStatus()) {
            AggregateLifecycle.apply(new PolicyMaintenanceStateAppliedEvent(
                    this.policyId, command.requestId(), command.requestPayloadHash().toLowerCase(),
                    command.sourceMaintenanceId(), endorsementNo, command.stateAction().name(),
                    com.titanium.policy.common.enums.EndorsementCategory.LIFECYCLE,
                    command.expectedPolicyVersion(), actualVersion, command.effectiveAt(), command.changeSummary(),
                    command.proposedSnapshotHash().toLowerCase(), originalSnapshotHash, snapshotStorageKey,
                    appliedSnapshotHash, applicationHash, execution.appliedFields(), execution.state(),
                    command.stateAction(), statusBefore, statusAfter, command.stateReason(),
                    command.terminationReason(), appliedAt, command.operatorId(), this.tenantId));
        } else {
            PolicyDataUpdateType updateType = maintenanceUpdateType(command.changes());
            AggregateLifecycle.apply(new PolicyMaintenanceAppliedEvent(
                    this.policyId, command.requestId(), command.requestPayloadHash().toLowerCase(),
                    command.sourceMaintenanceId(), endorsementNo, updateType,
                    updateType.getCategory(), command.expectedPolicyVersion(),
                    actualVersion, command.effectiveAt(), command.changeSummary(),
                    command.proposedSnapshotHash().toLowerCase(), originalSnapshotHash, snapshotStorageKey,
                    appliedSnapshotHash, applicationHash, execution.appliedFields(), execution.state(), appliedAt,
                    command.operatorId(), this.tenantId));
        }
        if (command.retroactiveEvidence() != null) {
            AggregateLifecycle.apply(new PolicyMaintenanceRetroactiveEvidenceRecordedEvent(
                    this.policyId, command.requestId(), command.sourceMaintenanceId(),
                    command.retroactiveEvidence(), appliedAt, command.operatorId(), this.tenantId));
        }
        return findMaintenanceApplication(command.requestId());
    }

    /**
     * 记录保费收讫（billing / payment 收费回调驱动，事件溯源）
     * <p>
     * 补齐此前的断链：原 {@code recordPayment(PaymentRecord)} 为普通方法且零调用方，收费事实
     * 无从进入保单，致 {@code collectionInfo} 恒无实收、保单生效校验被短路。
     * </p>
     * <p>
     * 幂等保护：同一支付流水不重复记账（收费回调 at-least-once 重投兜底）。
     * </p>
     */
    @CommandHandler
    public void handle(RecordPremiumCollectionCommand command) {
        if (command.collectedAmount() == null || command.collectedAmount().value().signum() <= 0) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "实收金额必须大于零");
        }
        if (command.paymentId() != null && this.paymentRecords != null && this.paymentRecords.stream()
                .anyMatch(record -> command.paymentId().equals(record.paymentId()))) {
            throw new PolicyBusinessRuleException("POLICY_PAYMENT_DUPLICATE",
                    "支付流水 " + command.paymentId() + " 已记账，忽略重复回调");
        }
        CollectionInfo current = this.collectionInfo;
        LocalDateTime collectedAt = command.collectedTime() != null ? command.collectedTime() : LocalDateTime.now();
        CollectionInfo collected = current != null
                ? current.collect(command.collectedAmount(), collectedAt)
                : CollectionInfo.initial(null, command.collectedAmount(), collectedAt)
                        .collect(command.collectedAmount(), collectedAt);
        AggregateLifecycle.apply(new PremiumCollectedEvent(this.policyId, command.paymentId(), command.paymentNo(),
                command.collectedAmount(), collected.collectedAmount(), collected.collectionStatus(),
                command.paymentMethod(), collectedAt, command.operatorId(), this.tenantId));
    }

    /**
     * 关联收费编排创建的账单与支付单。
     * <p>
     * 单据关联与实收事实分离：本命令不改变收讫金额，只把外部单据标识写入事件流。相同关联重复
     * 投递时直接忽略；已关联其他账单时拒绝覆盖，避免一张保单被静默改绑到另一张账单。
     * </p>
     */
    @CommandHandler
    public void handle(AssociatePremiumBillingCommand command) {
        if (command.billId() == null || command.billId().isBlank()) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "账单ID不能为空");
        }
        if (this.collectionInfo == null) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "保单缺少收费信息");
        }
        if (this.collectionInfo.billId() != null) {
            boolean sameBill = this.collectionInfo.billId().equals(command.billId());
            String currentPaymentOrderId = this.collectionInfo.paymentOrderId();
            boolean samePaymentOrder = Objects.equals(currentPaymentOrderId, command.paymentOrderId());
            if (sameBill && samePaymentOrder) {
                return;
            }
            // 账单开立后立即关联，支付单创建成功后允许在同一账单上补充一次支付单ID。
            if (sameBill && currentPaymentOrderId == null && command.paymentOrderId() != null) {
                CollectionInfo enriched = this.collectionInfo.withBilling(command.billId(), command.paymentOrderId());
                AggregateLifecycle.apply(new PremiumBillingAssociatedEvent(this.policyId, this.bizNo,
                        command.billId(), command.paymentOrderId(), enriched.collectionStatus(), this.tenantId));
                return;
            }
            throw new PolicyBusinessRuleException("POLICY_BILLING_ALREADY_ASSOCIATED", "保单已关联其他收费单据");
        }
        CollectionInfo associated = this.collectionInfo.withBilling(command.billId(), command.paymentOrderId());
        AggregateLifecycle.apply(new PremiumBillingAssociatedEvent(this.policyId, this.bizNo, command.billId(),
                command.paymentOrderId(), associated.collectionStatus(), this.tenantId));
    }

    @EventSourcingHandler
    public void on(PremiumBillingAssociatedEvent event) {
        if (this.collectionInfo != null) {
            this.collectionInfo = this.collectionInfo.withBilling(event.billId(), event.paymentOrderId());
        }
    }

    @EventSourcingHandler
    public void on(PremiumCollectedEvent event) {
        if (this.collectionInfo != null) {
            this.collectionInfo = this.collectionInfo.collect(event.collectedAmount(), event.collectedTime());
        }
        if (this.paymentRecords == null) {
            this.paymentRecords = new ArrayList<>();
        }
        this.paymentRecords.add(new PaymentRecord(event.paymentId(), event.paymentNo(), event.collectedAmount(),
                event.collectedTime(), event.paymentMethod(), null));
        // 缴费计划的缴费状态随收讫同步（收讫则视为已缴）
        if (this.premiumPlan != null && event.collectionStatus() != null && event.collectionStatus().allowsActivation()) {
            this.premiumPlan = new PremiumPlan(this.premiumPlan.premiumAmount(), this.premiumPlan.paymentMethod(),
                    this.premiumPlan.paymentCycle(), this.premiumPlan.premiumDueDate(), PremiumPlan.PaymentStatus.PAID);
        }
    }

    /**
     * 回写险种段核保结论（支撑主险通过 / 附加险拒保，事件溯源）
     * <p>
     * 拒保段的保费不计入保单总保费，故本命令会重算总保费并随事件携带，供读侧同步。
     * </p>
     */
    @CommandHandler
    public void handle(UpdateLineUnderwritingResultCommand command) {
        PolicyProduct line = lineOf(command.policyProductId());
        if (line == null) {
            throw new PolicyBusinessRuleException("POLICY_LINE_NOT_FOUND",
                    "险种段不存在: " + command.policyProductId());
        }
        if (command.conclusion() == null) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "核保结论不能为空");
        }
        PolicyProduct updated = line.withUnderwritingConclusion(command.conclusion());
        Money totalAfter = totalPremiumExcluding(command.policyProductId(), updated.effectivePremium());
        AggregateLifecycle.apply(new LineUnderwritingResultUpdatedEvent(this.policyId, command.policyProductId(),
                command.conclusion(), updated.lineStatus(), command.underwritingId(), command.opinion(), totalAfter,
                LocalDateTime.now(), command.operatorId(), this.tenantId));
    }

    @EventSourcingHandler
    public void on(LineUnderwritingResultUpdatedEvent event) {
        if (this.policyProducts == null) {
            return;
        }
        for (int i = 0; i < this.policyProducts.size(); i++) {
            PolicyProduct line = this.policyProducts.get(i);
            if (event.policyProductId().equals(line.policyProductId())) {
                this.policyProducts.set(i, line.withUnderwritingConclusion(event.conclusion()));
                break;
            }
        }
    }

    /**
     * 以指定段的新保费重算保单总保费（该段用新值，其余段用现值）。
     */
    private Money totalPremiumExcluding(String policyProductId, Money replacementPremium) {
        Money total = replacementPremium;
        if (this.policyProducts == null) {
            return total;
        }
        for (PolicyProduct line : this.policyProducts) {
            if (policyProductId.equals(line.policyProductId())) {
                continue;
            }
            Money linePremium = line.effectivePremium();
            if (linePremium == null) {
                continue;
            }
            total = total == null ? linePremium : total.add(linePremium);
        }
        return total;
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
        this.insuranceId = event.insuranceId();
        this.proposalId = event.proposalId();
        this.underwritingId = event.underwritingId();
        this.marketPackageId = event.marketPackageId();
        this.bizNo = event.bizNo();
        this.tenantId = event.tenantId();
        this.createTime = LocalDateTime.now();
        this.paymentRecords = new ArrayList<>();
        this.policyDocuments = new ArrayList<>();
        this.endorsements = new ArrayList<>();
        this.maintenanceApplications = new ArrayList<>();
        this.status = event.status();
        this.policyRelation = new PolicyRelation(PolicyEnum.PolicyLevel.INDEPENDENT, null, 0, null);
        // 险种段列表（L2）：事件携带则落地，缺失时置空列表（兼容存量事件；改造后出单链路必带）
        this.policyProducts = event.policyProducts() != null
                ? new ArrayList<>(event.policyProducts())
                : new ArrayList<>();
        // 期间/缴费/收费/渠道：事件直落，不再恒 null（此前 premiumPlan 恒 null 致收费校验被短路）
        this.policyPeriod = event.policyPeriod();
        this.premiumPlan = event.premiumPlan();
        this.collectionInfo = event.collectionInfo();
        this.channelInfo = event.channelInfo();
        this.totalPremium = event.premium();
        this.policyVersion = 0;
        // 优先使用事件携带的参与方清单（含真实投保人/被保险人/受益人快照）；事件无清单时初始化空清单以兼容存量事件
        this.insuredPartyList = event.insuredPartyList() != null
                ? event.insuredPartyList()
                : new InsuredPartyList(this.policyId, null, new ArrayList<>(), new ArrayList<>());
    }

    @EventSourcingHandler
    public void on(PolicyEndorsedEvent event) {
        // 版本真相唯一由此处递增产生（事件 versionAfter 仅审计）；批单记录取递增后的版本号
        incrementVersion();
        int currentVersion = this.policyVersion;
        if (this.endorsements == null) {
            this.endorsements = new ArrayList<>();
        }
        this.endorsements.add(new Endorsement(event.endorsementNo(), event.updateType(), event.category(),
                currentVersion, event.endorsementEffectiveDate(), event.changeSummary(), event.originalSnapshot(),
                event.requiresPremiumRecalc(), event.sourceMaintenanceId(), event.endorsedAt(), event.operatorId()));
    }

    @EventSourcingHandler
    public void on(PolicyMaintenanceAppliedEvent event) {
        applyMaintenanceExecutionState(event.executionStateAfter());
        incrementVersion();
        if (this.policyVersion != event.actualPolicyVersion()) {
            throw new IllegalStateException("Policy 保全应用事件版本与聚合版本不一致");
        }
        if (this.endorsements == null) {
            this.endorsements = new ArrayList<>();
        }
        this.endorsements.add(new Endorsement(
                event.endorsementNo(), event.updateType(), event.category(), this.policyVersion,
                event.effectiveAt(), event.changeSummary(), event.originalSnapshotHash(),
                event.updateType().needsPremiumRecalc(), event.sourceMaintenanceId(), event.appliedAt(),
                event.operatorId()));
        if (this.maintenanceApplications == null) {
            this.maintenanceApplications = new ArrayList<>();
        }
        this.maintenanceApplications.add(toReceipt(event));
    }

    @EventSourcingHandler
    public void on(PolicyMaintenanceStateAppliedEvent event) {
        if (this.status == null || this.status.statusCode() != event.statusBefore()) {
            throw new IllegalStateException("Policy 状态保全事件的变更前状态与聚合不一致");
        }
        applyMaintenanceExecutionState(event.executionStateAfter());
        incrementVersion();
        if (this.policyVersion != event.actualPolicyVersion()) {
            throw new IllegalStateException("Policy 状态保全事件版本与聚合版本不一致");
        }
        this.status = this.status.transitionStatus(
                event.statusAfter(), event.stateReason(), event.operatorId());
        if (event.stateAction() == PolicyMaintenanceAction.TERMINATE && this.annuityPayoutPlan != null) {
            this.annuityPayoutPlan = this.annuityPayoutPlan.stop();
        }
        if (this.maintenanceApplications == null) {
            this.maintenanceApplications = new ArrayList<>();
        }
        this.maintenanceApplications.add(toReceipt(event));
    }

    @EventSourcingHandler
    public void on(PolicyMaintenanceRetroactiveEvidenceRecordedEvent event) {
        if (this.maintenanceApplications == null) {
            throw new IllegalStateException("Policy追溯证据缺少对应保全应用回执");
        }
        for (int index = 0; index < this.maintenanceApplications.size(); index++) {
            PolicyMaintenanceApplicationReceipt receipt = this.maintenanceApplications.get(index);
            if (event.requestId().equals(receipt.requestId())) {
                this.maintenanceApplications.set(index, receipt.withRetroactiveEvidence(event.evidence()));
                return;
            }
        }
        throw new IllegalStateException("Policy追溯证据缺少对应请求ID");
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

    // ==================== 险种段导航与保单构成 ====================

    /**
     * 主险段（一张保单有且仅有一个）。
     *
     * @return 主险段；段列表为空时返回 null
     */
    public PolicyProduct mainLine() {
        if (this.policyProducts == null) {
            return null;
        }
        return this.policyProducts.stream().filter(PolicyProduct::isMain).findFirst().orElse(null);
    }

    /**
     * 附加险段列表（依附于主险，可为空）。
     *
     * @return 附加险段列表
     */
    public List<PolicyProduct> riderLines() {
        if (this.policyProducts == null) {
            return List.of();
        }
        return this.policyProducts.stream().filter(PolicyProduct::isRider).toList();
    }

    /**
     * 按段ID查找险种段。
     *
     * @param policyProductId 险种段ID
     * @return 匹配的险种段；未找到返回 null
     */
    public PolicyProduct lineOf(String policyProductId) {
        if (this.policyProducts == null || policyProductId == null) {
            return null;
        }
        return this.policyProducts.stream()
                .filter(line -> policyProductId.equals(line.policyProductId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 保单总保费（Σ 计入段的保费，拒保段不计入）。
     * <p>
     * 段列表为空时回退取基本信息中的总保费（兼容存量单险种保单）。
     * </p>
     *
     * @return 总保费；无从计算时返回 null
     */
    public Money totalPremium() {
        if (this.policyProducts == null || this.policyProducts.isEmpty()) {
            return this.totalPremium;
        }
        Money total = null;
        for (PolicyProduct line : this.policyProducts) {
            Money linePremium = line.effectivePremium();
            if (linePremium == null) {
                continue;
            }
            total = total == null ? linePremium : total.add(linePremium);
        }
        return total;
    }

    /**
     * 本保单包含的险种段数量（单险种保单为 1）。
     *
     * @return 险种段数量
     */
    public int lineCount() {
        return this.policyProducts != null ? this.policyProducts.size() : 0;
    }

    // ==================== 期间与收费判定 ====================

    /**
     * 当前是否处于等待期内（疾病类责任此期间不赔）。
     *
     * @return 在等待期内返回 {@code true}
     */
    @JsonIgnore
    public boolean isInWaitingPeriod() {
        return this.policyPeriod != null && this.policyPeriod.isInWaitingPeriod(LocalDateTime.now());
    }

    /**
     * 当前是否处于犹豫期内（投保人可无条件退保，仅扣工本费）。
     *
     * @return 在犹豫期内返回 {@code true}
     */
    @JsonIgnore
    public boolean isInHesitationPeriod() {
        return this.policyPeriod != null && this.policyPeriod.isInHesitationPeriod(LocalDateTime.now());
    }

    /**
     * 保单是否满足全部生效条件（收费条件 + 保障起期已到 + 当前为未生效态）。
     *
     * @return 可生效返回 {@code true}
     */
    public boolean canActivate() {
        return this.status != null && this.status.statusCode() == PolicyStatus.StatusCode.NOT_EFFECTIVE
                && isPremiumConditionSatisfied()
                && (this.policyPeriod == null || this.policyPeriod.hasStarted(LocalDateTime.now()));
    }

    /**
     * 保费条件是否满足生效要求。
     * <p>
     * 有收费信息时以其判定（已收讫或先享后付放行）；无收费信息时退回缴费计划判定，
     * 二者皆无则放行（兼容尚未接入收费链路的存量保单）。
     * </p>
     */
    private boolean isPremiumConditionSatisfied() {
        if (this.collectionInfo != null) {
            return this.collectionInfo.allowsActivation();
        }
        if (this.premiumPlan != null) {
            return this.premiumPlan.paymentStatus() != PremiumPlan.PaymentStatus.UNPAID;
        }
        return true;
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
            member = new InsuredPartyList.InsuredInfo(member.customerId(), member.insuredId(), member.name(),
                    member.certType(), member.certNo(), member.age(), member.gender(), member.phone(),
                    member.relationToHolder(), event.familyRelation());
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
     * 业务版本号递增（保全域数据变更/批改后调用）
     * <p>
     * 版本真相唯一由此产生（{@code PolicyEndorsedEvent.versionAfter} 仅作审计快照）。
     * </p>
     */
    public void incrementVersion() {
        this.policyVersion++;
    }

    // ==================== 内部方法 ====================

    private void validateMaintenanceRequestIdentity(ApplyPolicyMaintenanceCommand command) {
        if (!Objects.equals(this.policyId, command.policyId()) || !Objects.equals(this.tenantId, command.tenantId())) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_CONTEXT_INVALID", "Policy 保全请求的聚合或租户上下文不一致");
        }
        if (isBlank(command.requestId()) || isBlank(command.sourceMaintenanceId()) || isBlank(command.operatorId())) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_REQUEST_INVALID", "请求ID、案件ID和操作人不能为空");
        }
        if (!isSha256(command.requestPayloadHash()) || !isSha256(command.proposedSnapshotHash())) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_REQUEST_INVALID", "请求摘要和拟变更快照摘要必须为 SHA-256");
        }
    }

    private void validateNewMaintenanceRequest(ApplyPolicyMaintenanceCommand command) {
        if (isBlank(command.changeSummary())) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_REQUEST_INVALID", "变更摘要不能为空");
        }
        if (!SUPPORTED_MAINTENANCE_EFFECTIVE_TIME_TYPES.contains(command.effectiveTimeType())
                || command.effectiveAt() == null) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_EFFECTIVE_TIME_UNSUPPORTED", "当前生效时态不受 Policy 正式应用支持");
        }
        if ("IMMEDIATE".equals(command.effectiveTimeType())
                && command.effectiveAt().isAfter(LocalDateTime.now().plusMinutes(5))) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_EFFECTIVE_TIME_UNSUPPORTED", "保全生效时间尚未到达");
        }
        if ("RETROACTIVE".equals(command.effectiveTimeType())) {
            if (command.retroactiveEvidence() == null
                    || command.effectiveAt().isAfter(LocalDateTime.now())) {
                throw new PolicyBusinessRuleException(
                        "POLICY_MAINTENANCE_RETROACTIVE_EVIDENCE_REQUIRED",
                        "追溯生效必须包含完整跨域证据且生效时间不能晚于当前时间");
            }
        } else if (command.retroactiveEvidence() != null) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_RETROACTIVE_EVIDENCE_INVALID",
                    "非追溯保全不得提交追溯证据");
        }
        if (this.status == null) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_STATUS_INVALID", "Policy 缺少当前合同状态");
        }
        PolicyMaintenanceAction action = command.stateAction();
        if (command.changes().isEmpty() && !action.changesStatus()) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_REQUEST_INVALID", "字段变更和状态动作不能同时为空");
        }
        if (!action.changesStatus()) {
            if (this.status.statusCode() != PolicyStatus.StatusCode.EFFECTIVE) {
                throw new PolicyBusinessRuleException(
                        "POLICY_MAINTENANCE_STATUS_INVALID", "仅生效保单可应用字段型保全变更");
            }
            if (!isBlank(command.stateReason()) || command.terminationReason() != null) {
                throw new PolicyBusinessRuleException(
                        "POLICY_MAINTENANCE_REQUEST_INVALID", "无状态动作时不得提交状态原因或终止原因");
            }
            return;
        }
        if (isBlank(command.stateReason())) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_REQUEST_INVALID", "状态类保全必须包含变更原因");
        }
        if (action == PolicyMaintenanceAction.TERMINATE && command.terminationReason() == null) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_REQUEST_INVALID", "终止保单必须包含终止原因");
        }
        if (action != PolicyMaintenanceAction.TERMINATE && command.terminationReason() != null) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_REQUEST_INVALID", "非终止动作不得包含终止原因");
        }
        maintenanceStatusAfter(action, this.status.statusCode());
    }

    private PolicyStatus.StatusCode maintenanceStatusAfter(
            PolicyMaintenanceAction action,
            PolicyStatus.StatusCode currentStatus) {
        return switch (action) {
            case NONE -> currentStatus;
            case SUSPEND -> {
                requireMaintenanceStatus(currentStatus, PolicyStatus.StatusCode.EFFECTIVE, action);
                yield PolicyStatus.StatusCode.SUSPENDED;
            }
            case RESUME -> {
                requireMaintenanceStatus(currentStatus, PolicyStatus.StatusCode.SUSPENDED, action);
                yield PolicyStatus.StatusCode.EFFECTIVE;
            }
            case REINSTATE -> {
                requireMaintenanceStatus(currentStatus, PolicyStatus.StatusCode.LAPSED, action);
                yield PolicyStatus.StatusCode.EFFECTIVE;
            }
            case TERMINATE -> {
                if (currentStatus != PolicyStatus.StatusCode.EFFECTIVE
                        && currentStatus != PolicyStatus.StatusCode.SUSPENDED
                        && currentStatus != PolicyStatus.StatusCode.LAPSED) {
                    throw new PolicyBusinessRuleException(
                            "POLICY_MAINTENANCE_STATUS_INVALID",
                            "仅 EFFECTIVE、SUSPENDED 或 LAPSED 保单可终止");
                }
                yield PolicyStatus.StatusCode.TERMINATED;
            }
        };
    }

    private void requireMaintenanceStatus(
            PolicyStatus.StatusCode actual,
            PolicyStatus.StatusCode expected,
            PolicyMaintenanceAction action) {
        if (actual != expected) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_STATUS_INVALID",
                    action.name() + " 要求保单状态为 " + expected + "，实际为 " + actual);
        }
    }

    private PolicyMaintenanceApplicationReceipt findMaintenanceApplication(String requestId) {
        if (this.maintenanceApplications == null || requestId == null) {
            return null;
        }
        return this.maintenanceApplications.stream()
                .filter(application -> requestId.equals(application.requestId()))
                .findFirst()
                .orElse(null);
    }

    private PolicyMaintenanceApplicationReceipt toReceipt(PolicyMaintenanceAppliedEvent event) {
        PolicyMaintenanceSnapshotReference snapshot = new PolicyMaintenanceSnapshotReference(
                event.appliedSnapshotStorageKey(), event.appliedSnapshotContentHash(),
                event.actualPolicyVersion(), event.appliedAt().atOffset(ZoneOffset.ofHours(8)));
        return new PolicyMaintenanceApplicationReceipt(
                event.requestId(), event.requestPayloadHash(), event.endorsementNo(),
                event.expectedPolicyVersion(), event.actualPolicyVersion(), event.applicationHash(),
                snapshot, event.appliedFields(), event.appliedAt());
    }

    private PolicyMaintenanceApplicationReceipt toReceipt(PolicyMaintenanceStateAppliedEvent event) {
        PolicyMaintenanceSnapshotReference snapshot = new PolicyMaintenanceSnapshotReference(
                event.appliedSnapshotStorageKey(), event.appliedSnapshotContentHash(),
                event.actualPolicyVersion(), event.appliedAt().atOffset(ZoneOffset.ofHours(8)));
        return new PolicyMaintenanceApplicationReceipt(
                event.requestId(), event.requestPayloadHash(), event.endorsementNo(),
                event.expectedPolicyVersion(), event.actualPolicyVersion(), event.applicationHash(),
                snapshot, event.appliedFields(), event.appliedAt(), event.stateAction(),
                event.statusBefore(), event.statusAfter());
    }

    private String maintenanceSnapshotHash(
            long version,
            PolicyMaintenanceExecutionState executionState,
            PolicyStatus.StatusCode snapshotStatus) {
        PolicyProduct mainProduct = requireMainProductForMaintenance(executionState.policyProducts());
        Map<String, PolicyMaintenanceSnapshotFieldValue> fields = maintenanceSnapshotFields(
                mainProduct, executionState.insuredPartyList(), snapshotStatus);
        return PolicyMaintenanceHashing.snapshotHash(
                this.tenantId, this.policyId, version, mainProduct.productId(),
                mainProduct.productVersion(), mainProduct.pricingPlanVersion(), fields);
    }

    private Map<String, PolicyMaintenanceSnapshotFieldValue> maintenanceSnapshotFields(
            PolicyProduct mainProduct,
            InsuredPartyList parties,
            PolicyStatus.StatusCode snapshotStatus) {
        TreeMap<String, PolicyMaintenanceSnapshotFieldValue> fields = new TreeMap<>();
        InsuredPartyList.HolderInfo holder = parties != null ? parties.holderInfo() : null;
        Money lineCurrencySource = mainProduct.premium() != null ? mainProduct.premium() : mainProduct.sumInsured();
        fields.put("policy.collection.mode", snapshotField("ENUM",
                this.collectionInfo != null && this.collectionInfo.collectionMode() != null
                        ? this.collectionInfo.collectionMode().getCode() : null));
        fields.put("policy.coverage.sumInsured", snapshotField("DECIMAL", amount(mainProduct.sumInsured())));
        fields.put("policy.currency", snapshotField("ENUM",
                lineCurrencySource != null ? lineCurrencySource.currency() : null));
        fields.put("policy.holder.id", snapshotField("TEXT", holder != null ? holder.customerId() : null));
        fields.put("policy.holder.mobile", snapshotField("TEXT", holder != null ? holder.phone() : null));
        fields.put("policy.holder.name", snapshotField("TEXT", holder != null ? holder.name() : null));
        fields.put("policy.number", snapshotField("TEXT", this.policyNo != null ? this.policyNo.value() : null));
        fields.put("policy.period.end", snapshotField("DATETIME", dateTime(
                this.policyPeriod != null ? this.policyPeriod.insurancePeriodEnd() : null)));
        fields.put("policy.period.start", snapshotField("DATETIME", dateTime(
                this.policyPeriod != null ? this.policyPeriod.insurancePeriodStart() : null)));
        fields.put("policy.premium.total", snapshotField("DECIMAL", amount(this.totalPremium)));
        fields.put("policy.product.id", snapshotField("TEXT", mainProduct.productId()));
        fields.put("policy.product.planVersion", snapshotField("TEXT", mainProduct.pricingPlanVersion()));
        fields.put("policy.product.version", snapshotField("TEXT", mainProduct.productVersion()));
        fields.put("policy.status", snapshotField("ENUM", snapshotStatus.name()));
        return Map.copyOf(fields);
    }

    private PolicyProduct requireMainProductForMaintenance(List<PolicyProduct> products) {
        List<PolicyProduct> mainProducts = products == null ? List.of() : products.stream()
                .filter(Objects::nonNull)
                .filter(PolicyProduct::isMain)
                .toList();
        if (mainProducts.size() != 1) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_CONTRACT_INCOMPLETE", "Policy 必须且只能存在一个主险段");
        }
        PolicyProduct mainProduct = mainProducts.getFirst();
        if (isBlank(mainProduct.productId()) || isBlank(mainProduct.productVersion())
                || isBlank(mainProduct.pricingPlanVersion())) {
            throw new PolicyBusinessRuleException(
                    "POLICY_MAINTENANCE_CONTRACT_INCOMPLETE", "Policy 缺少产品或定价计划版本");
        }
        return mainProduct;
    }

    private void applyMaintenanceExecutionState(PolicyMaintenanceExecutionState executionState) {
        if (executionState == null) {
            return;
        }
        if (executionState.insuredPartyList() != null) {
            this.insuredPartyList = executionState.insuredPartyList();
        }
        if (executionState.policyProducts() != null) {
            this.policyProducts = List.copyOf(executionState.policyProducts());
            this.sumInsured = requireMainProductForMaintenance(this.policyProducts).sumInsured();
        }
    }

    private PolicyDataUpdateType maintenanceUpdateType(List<PolicyMaintenanceFieldChange> changes) {
        List<PolicyDataUpdateType> updateTypes = changes.stream()
                .map(PolicyMaintenanceFieldChange::itemCode)
                .map(PolicyDataUpdateType::byMaintenanceType)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return updateTypes.size() == 1 ? updateTypes.getFirst() : PolicyDataUpdateType.POLICY_INFO_CHANGE;
    }

    private PolicyMaintenanceSnapshotFieldValue snapshotField(String dataType, String value) {
        return new PolicyMaintenanceSnapshotFieldValue(dataType, value);
    }

    private String amount(Money money) {
        return money == null || money.value() == null
                ? null : money.value().stripTrailingZeros().toPlainString();
    }

    private String dateTime(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.ofHours(8)).toString();
    }

    private boolean isSha256(String value) {
        return value != null && value.matches("[a-fA-F0-9]{64}");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void generateDocument() {
        PolicyDocument document = new PolicyDocument("doc-" + LocalDateTime.now(), "E-" + this.policyNo.value(),
                "P-" + this.policyNo.value(), LocalDateTime.now(), PolicyEnum.SignatureStatus.UNSIGNED,
                "http://docs.titanium.com/policies/" + this.policyNo.value());
        this.policyDocuments.add(document);
    }

    protected Policy() {
    }
}
