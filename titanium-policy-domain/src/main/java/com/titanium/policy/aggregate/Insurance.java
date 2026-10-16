package com.titanium.policy.aggregate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.valueobject.Money;
import com.titanium.metadata.valueobject.RuleDecision;
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
import com.titanium.policy.valueobject.product.ProductSumInsuredTiers;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 投保单聚合根
 * <p>
 * 作为投保单聚合的唯一对外交互入口，管理投保信息、核保对接、承保出单全流程。
 * </p>
 */
@Aggregate
@Getter
@Slf4j
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
        validateSumInsuredTiers(command.insuranceLines(), command.sumInsuredTiers());
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
        validateSumInsuredTiers(command.insuranceLines(), command.sumInsuredTiers());
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
        requireSameTenant(command.tenantId());
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
        requireSameTenant(command.tenantId());
        UnderwritingResult result = command.underwritingResult();
        if (this.status.statusCode() != InsuranceStatusCode.UNDERWRITING) {
            // 🔴 幂等分支（D-501-15）：跨域事件的重投与迟到是分布式常态（消费者重启、位点回退、
            // 上游重发），同一份结论再次到达时状态已越过 UNDERWRITING。此时「已按同一核保单号落过
            // 同一结论」是重投而非非法调用，按幂等成功处理（不发事件、不改状态）。
            // 修复前一律抛异常，异常逃逸到消费端后零间隔重试 10 次仍无法自愈，最终消息被静默丢弃。
            if (isRedeliveredResult(result)) {
                return;
            }
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION",
                    "Only applications in underwriting can receive results");
        }
        AggregateLifecycle.apply(new UnderwritingResultReceivedEvent(this.insuranceId, result.underwritingId(),
                result.resultCode(), result.underwritingOpinion(), result.underwriterId(), result.underwritingTime(),
                result.condition(), this.tenantId, result.extraPremiumRatio(), this.bizNo));
    }

    /**
     * 判定本次结论是否为已落库同一结论的重投/迟到投递。
     * <p>
     * 判据是「核保单号一致 <b>且</b> 结论一致」：前者标识同一份核保决定，后者确认结论未被改判。
     * 二者缺一即非重投——例如同一投保单换了新的核保单号（重新核保），或同号结论被上游改判，
     * 都属需要暴露的真实异常，不得按幂等静默吞掉。
     * </p>
     *
     * @param incoming 本次到达的核保结论
     * @return 属重投返回 {@code true}
     */
    private boolean isRedeliveredResult(UnderwritingResult incoming) {
        return incoming != null
                && incoming.underwritingId() != null
                && this.underwritingResult != null
                && incoming.underwritingId().equals(this.underwritingResult.underwritingId())
                && incoming.resultCode() == this.underwritingResult.resultCode();
    }

    /**
     * 触发承保出单
     */
    @CommandHandler
    public void handle(TriggerIssuanceCommand command) {
        requireSameTenant(command.tenantId());
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
            case ACCEPT, MODIFY, EXCLUDED -> InsuranceStatusCode.UNDERWRITING_APPROVED;
            case REJECT -> InsuranceStatusCode.UNDERWRITING_REJECTED;
            case POSTPONE -> InsuranceStatusCode.UNDERWRITING_SUSPENDED;
        };
        String changeReason = switch (resultCode) {
            case ACCEPT -> "核保通过";
            case MODIFY -> "核保通过（修改条件承保）";
            case EXCLUDED -> "核保通过（除外承保）";
            case REJECT -> "核保拒绝";
            case POSTPONE -> "核保暂缓";
        };
        this.status = this.status.transitionStatus(newStatus, changeReason);
        // 整单结论下发到各险种段：核保域当前只出具单据级结论，段级差异化结论（主险通过/附加险拒保）
        // 尚无上游支撑——核保域 UnderwritingDecidedEvent 载荷不含分段结论字段。
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
     * **首个**主险段（按段列表顺序取第一个主险段，<b>不表示主险段唯一</b>）。
     * <p>
     * 🔴 G11/g11-04 方案 B：一张投保单<b>可含多个主险段</b>（如交强险 + 商业车险同单），
     * 旧语义「有且仅有一个主险段」已不成立。本方法仅供「按首个主险段导航」的既有场景使用，
     * 不得据此认定唯一主险；需要完整主险段集合时，调用方自行按 {@code InsuranceLine::isMain} 过滤段列表。
     * </p>
     *
     * @return 首个主险段；无段时返回 null
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
     * 投保要素的业务校验（年龄/职业等依产品条件）由 application 层的
     * {@code IssuanceEligibilityDomainService} 在出单受理阶段完成，不在此重复。
     * </p>
     * <p>
     * 🔴 <b>例外——段级保额的责任档次接纳性</b>（G02/AC-09）落在创建构造器而非受理阶段：
     * 档次配置是产品域的<b>责任级</b>契约，与受理阶段读的 {@code ProductIssueRules}
     * （{@code insureCondition} 的 min/max 保额）<b>不同源</b>；且直连建单端点不经受理阶段，
     * 放在那里会被整条路径绕过。实现见 {@link #validateSumInsuredTiers}。
     * </p>
     */
    private void validateInsuranceLines() {
        validateInsuranceLines(this.insuranceLines);
    }

    /**
     * 在首个事件写入前校验投保险种段，避免创建无法进入核保和承保流程的坏草稿。
     * <p>
     * 🔴 <b>G11/g11-04 ⑨（第二道「唯一主险」防线）</b>：交强 + 商业同单（方案 B）下本单可含
     * <b>多个主险段</b>，原「有且仅有一个主险」判据会在<b>投保单创建</b>即拒——比保单段构成
     * （不变量①）更早，只改保单侧会被此处的硬约束拦在门外。放宽口径与
     * {@code PolicyCompositionDomainServiceImpl.validateMainLines/validateRiderParents} 逐字一致：
     * <b>≥1 个主险段</b>（{@code mainCount == 0} 仍拒）+ 主险段间 <b>productId 唯一</b>
     * （🔴 互斥键必须是 productId：交强与商业同属 AUTO 一个险种，用险种或险别做键会把要放行的
     * 双主险单自己拒掉）+ 附加险父段 ∈ <b>主险段 id 集合</b>（原 {@code findFirst()} 只认首个主险，
     * 挂第二个主险的附加险会被误拒）。
     * </p>
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
        if (mainCount == 0) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "投保单须含至少一个主险段");
        }
        Set<String> mainProductIds = new HashSet<>();
        Set<String> mainLineIds = new HashSet<>();
        for (InsuranceLine line : lines) {
            if (!line.isMain()) {
                continue;
            }
            if (!mainProductIds.add(line.productId())) {
                throw new PolicyBusinessRuleException(PolicyErrorCode.COMPOSITION_MULTIPLE_MAIN_LINES,
                        "同一险种只能有一个主险段：产品 " + line.productId() + " 出现多个主险段");
            }
            mainLineIds.add(line.lineId());
        }
        for (InsuranceLine line : lines) {
            if (line.isRider() && (line.parentLineId() == null || !mainLineIds.contains(line.parentLineId()))) {
                throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION",
                        "附加险段须依附于本单主险段: 段序号 " + line.lineNo());
            }
        }
    }

    /**
     * 校验各险种段的保额是否被产品已配置的责任档次接纳（G02/AC-09）。
     * <p>
     * 产品域为每条责任配置「档位清单 + 可填区间 + 是否开放自由填」，段级录入的保额须被
     * <b>至少一条</b>责任接纳。配置由 application 编排经 {@code ProductServicePort} 取回后
     * 作为<b>命令分量</b>传入——聚合不得依赖 Port（领域纯粹性）。
     * </p>
     * <p>
     * 🔴 <b>三态语义</b>：
     * </p>
     * <ol>
     *   <li>{@code tiers} 为 {@code null}（调用方未接线）而段上确有保额 ⇒ <b>拒绝</b>：
     *       失败关闭，防止新增建单入口漏传配置后静默绕过档次校验；</li>
     *   <li>产品无对应条目或条目内无任何责任档次 ⇒ <b>放行 + 留痕</b>：G01 已定「不配置是
     *       合法的中间状态」，在此拒绝会让存量未配置档次的产品全部出不了单；</li>
     *   <li>有条目但 0/N 条责任接纳 ⇒ <b>拒绝</b>，报文携带各责任可选档位与区间便于改选。</li>
     * </ol>
     * <p>
     * 段上保额为空时跳过本规则——「保额必填」由受理阶段的产品投保条件校验守护，
     * 不在两处各写一份判据。
     * </p>
     *
     * @param lines 投保险种段列表
     * @param tiers 各产品责任级档次配置；{@code null} 表示调用方未提供
     */
    private static void validateSumInsuredTiers(List<InsuranceLine> lines, List<ProductSumInsuredTiers> tiers) {
        RuleDecision decision = checkSumInsuredTiers(lines, tiers);
        if (!decision.passed()) {
            throw new PolicyBusinessRuleException(decision.errorCode(), decision.defaultMessage());
        }
    }

    /**
     * 判定各险种段的保额是否被产品档次接纳（纯函数，无副作用，便于单测）。
     *
     * @param lines 投保险种段列表
     * @param tiers 各产品责任级档次配置；{@code null} 表示调用方未提供
     * @return 判定结果；拒绝时携带错误码与首个不接纳的段序号
     */
    private static RuleDecision checkSumInsuredTiers(List<InsuranceLine> lines, List<ProductSumInsuredTiers> tiers) {
        if (lines == null) {
            return RuleDecision.accepted();
        }
        for (InsuranceLine line : lines) {
            if (line == null || line.sumInsured() == null || line.sumInsured().value() == null) {
                continue;
            }
            BigDecimal amount = line.sumInsured().value();
            if (tiers == null) {
                // 未接线：段上确有保额却拿不到任何档次配置，无从校验 ⇒ 失败关闭
                return RuleDecision.rejectedAtLine(PolicyErrorCode.ELIGIBILITY_SUM_INSURED_TIER_CONFIG_MISSING,
                        line.lineNo(), amount);
            }
            ProductSumInsuredTiers productTiers = findSumInsuredTiers(tiers, line.productId());
            if (productTiers == null || !productTiers.hasConfig()) {
                // 该产品未配置责任档次：合法中间状态，放行但留痕（存量产品不受阻）
                log.warn("产品未配置责任级保额档次，段级保额档次校验放行: productId={}, lineNo={}, sumInsured={}",
                        line.productId(), line.lineNo(), amount);
                continue;
            }
            if (!productTiers.acceptsSumInsured(amount)) {
                return RuleDecision.rejectedAtLine(PolicyErrorCode.ELIGIBILITY_SUM_INSURED_TIER_NOT_ACCEPTED,
                        line.lineNo(), amount, line.productId(), productTiers.coverages().size(),
                        productTiers.describeOptions());
            }
        }
        return RuleDecision.accepted();
    }

    /**
     * 在档次配置载体集合中按产品ID查找条目。
     *
     * @param tiers     各产品责任级档次配置
     * @param productId 产品ID
     * @return 匹配的条目；该产品未配置时返回 {@code null}
     */
    private static ProductSumInsuredTiers findSumInsuredTiers(List<ProductSumInsuredTiers> tiers, String productId) {
        if (productId == null) {
            return null;
        }
        return tiers.stream()
                .filter(item -> item != null && productId.equals(item.productId()))
                .findFirst()
                .orElse(null);
    }

    private void ensureDraftStatus() {
        if (this.status.statusCode() != InsuranceStatusCode.DRAFT) {
            throw new PolicyBusinessRuleException("POLICY_RULE_VIOLATION", "Only draft applications can be modified");
        }
    }

    /** 仅供 Axon 回放使用（框架反射实例化），业务代码禁止调用 */
    protected Insurance() {
    }

    /**
     * 校验命令租户与聚合租户一致。
     * <p>
     * 🔴 <b>多租户写侧最后一道防线</b>：Axon 按聚合标识装载、事件流不按租户隔离，
     * 聚合的租户归属只能由聚合自身守护。命令的 {@code tenantId} 来自调用方请求头
     * （{@code X-Tenant-Id}），可被伪造——缺此校验时，持他租户的聚合ID即可跨租户写入。
     * </p>
     * <p>
     * <b>失败关闭</b>：租户缺失或与聚合不一致一律拒绝，并以
     * {@link PolicyErrorCode.INSURANCE_NOT_EXIST} 对外（不泄漏「资源是否存在」这一侧信道）。
     * </p>
     *
     * @param commandTenantId 命令携带的租户ID
     */
    private void requireSameTenant(String commandTenantId) {
        if (commandTenantId == null || commandTenantId.isBlank()
                || !commandTenantId.equals(this.tenantId)) {
            throw new PolicyBusinessRuleException(PolicyErrorCode.INSURANCE_NOT_EXIST, "投保单不存在");
        }
    }


    /** insuranceLines 不可变视图（DSN-02：杜绝经 getter 外泄内部集合引用，变更须经命令与事件） */
    public List<InsuranceLine> getInsuranceLines() {
        return insuranceLines == null ? null : List.copyOf(insuranceLines);
    }
}
