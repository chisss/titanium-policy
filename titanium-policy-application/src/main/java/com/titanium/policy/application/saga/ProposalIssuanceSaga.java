package com.titanium.policy.application.saga;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.SagaLifecycle;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;

import com.titanium.common.context.RequestContextHolder;
import com.titanium.metadata.enums.BaseEnum;
import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.billing.PremiumCollectionMode;
import com.titanium.metadata.enums.insurance.InsuranceCategory;
import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.policy.PolicyLineStatus;
import com.titanium.metadata.enums.product.ProductEnum.PaymentFrequency;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.application.orchestration.issuance.service.ProductSumInsuredTiersLoader;
import com.titanium.policy.command.ConvertProposalCommand;
import com.titanium.policy.command.ConvertProposalToInsuranceCommand;
import com.titanium.policy.command.SubmitUnderwritingCommand;
import com.titanium.policy.entity.insurance.InsuranceLine;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.entity.policy.InsuredSubject;
import com.titanium.policy.entity.proposal.ProposalLine;
import com.titanium.policy.entity.proposal.ProposalSubject;
import com.titanium.policy.event.proposal.ProposalConvertedEvent;
import com.titanium.policy.event.proposal.ProposalCreatedEvent;
import com.titanium.policy.event.proposal.ProposalSubmittedEvent;
import com.titanium.policy.event.proposal.ProposalVoidedEvent;
import com.titanium.policy.generator.PolicyNoGenerator;
import com.titanium.policy.valueobject.policy.ChannelInfo;
import com.titanium.policy.valueobject.policy.LineCoveragePeriod;
import com.titanium.policy.valueobject.policy.LinePaymentTerms;

import lombok.extern.slf4j.Slf4j;

/**
 * 意向单出单 Saga（意向单提交 -> 转投保单 -> 创建投保单 自动接力）
 * <p>
 * 以 {@code proposalId} 为关联键，消除三步出单中 Proposal SUBMITTED 状态停滞的断点：
 * </p>
 * <ol>
 *     <li><b>意向单创建</b>：{@link ProposalCreatedEvent} 启动 Saga，记忆后续创建投保单所需业务数据；</li>
 *     <li><b>意向单提交</b>：{@link ProposalSubmittedEvent} 触发，自动发 {@link ConvertProposalCommand}
 *         将意向单状态流转为 {@code CONVERTED_TO_APPLICATION}；</li>
 *     <li><b>意向单转换</b>：{@link ProposalConvertedEvent} 触发，用记忆数据构造
 *         {@link ConvertProposalToInsuranceCommand}，创建投保单聚合，启动 {@code IssuanceSaga} 接力；
 *         随后结束本 Saga。</li>
 *     <li><b>意向单作废</b>：{@link ProposalVoidedEvent} 触发时提前结束 Saga（无后续流程）。</li>
 * </ol>
 * <p>
 * 本 Saga 仅负责 Proposal 生命周期内的接力，后续核保/承保/出单由 {@code IssuanceSaga} 事件驱动完成。
 * </p>
 */
@Slf4j
@Saga
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE,
        isGetterVisibility = Visibility.NONE)
public class ProposalIssuanceSaga {


    /**
     * Saga 内同步命令的超时（秒）。
     * <p>m21-05（审查报告 ORC-02）：{@code sendAndWait(command)} 的无超时重载在下游不响应时
     * <b>永久阻塞 Saga 事件处理线程</b>——该线程被 Axon 用于顺序处理同一处理器组的事件，
     * 一条卡住的命令会让整组停止推进，且 Saga 自身的超时机制（DeadlineManager）无从介入。
     * 故 Saga 内的同步命令一律带超时：超时后抛异常，由错误处理器使该次出单挂起并告警。</p>
     */
    private static final long COMMAND_TIMEOUT_SECONDS = 5L;

    @Autowired
    private transient CommandGateway commandGateway;

    @Autowired
    private transient PolicyNoGenerator policyNoGenerator;

    /**
     * 段级保额档次配置取数器（G02/AC-09）。
     * <p>与 {@code IssuanceOrchestrator} 共用同一份取数规则（按产品去重、远程失败不兜底），
     * 结果作为命令分量交给投保单聚合——聚合不得依赖 Port。</p>
     */
    @Autowired
    private transient ProductSumInsuredTiersLoader productSumInsuredTiersLoader;

    /** 保单形态 */
    private PolicyForm policyForm;
    /** 客户ID（投保人），供构造投保单命令的 applicantId */
    private String customerId;
    /** 意向保费（客户预算或粗略报价，不作为实际保费） */
    private BigDecimal intendedPremium;
    /** 保障起期 */
    private LocalDateTime insurancePeriodStart;
    /** 保障止期 */
    private LocalDateTime insurancePeriodEnd;
    /** 期望险种编码（单险种意向的遗留字段；多险种意向以 proposalLines 承载） */
    private String expectedProductCode;
    /** 意向险种段列表（多险种意向组合，转投保单时精化为投保段） */
    private List<ProposalLine> proposalLines;
    /** 意向标的摘要，物类标的在转投保单时恢复为完整标的。 */
    private List<ProposalSubject> proposalSubjects;
    /** 出单业务流水号（幂等与进度追溯，透传投保单） */
    private String bizNo;
    /** 营销包ID（弱引用，透传投保单） */
    private String marketPackageId;
    /** 险种三级分类（可空） */
    private InsuranceProductType insuranceType;
    /** 租户ID */
    private String tenantId;
    /** 参与方清单快照 */
    private InsuredPartyList insuredPartyList;
    /** 收费方式 */
    private PremiumCollectionMode collectionMode;
    /** 渠道信息 */
    private ChannelInfo channelInfo;
    /** 主险缴费模式 */
    private String paymentMode;
    /** 主险缴费年数 */
    private int premiumPaymentYears;
    /** 主险意向保额 */
    private BigDecimal intendedSumInsured;

    /**
     * 【意向单创建】启动 Saga，记忆后续构造投保单命令所需数据
     */
    @StartSaga
    @SagaEventHandler(associationProperty = "proposalId")
    public void on(ProposalCreatedEvent event) {
        inTenantScope(event.tenantId(), event.proposalId(), () -> rememberProposalCreation(event));
    }

    /** 【意向单创建】记忆后续构造投保单命令所需数据。 */
    private void rememberProposalCreation(ProposalCreatedEvent event) {
        log.info("[ProposalIssuanceSaga] 启动: proposalId={}, tenantId={}",
                event.proposalId(), event.tenantId());
        this.policyForm = event.policyForm();
        this.customerId = event.customerId();
        this.intendedPremium = event.intendedPremium();
        this.intendedSumInsured = event.intendedSumInsured();
        this.insurancePeriodStart = event.insurancePeriodStart();
        this.insurancePeriodEnd = event.insurancePeriodEnd();
        this.expectedProductCode = event.expectedProductCode();
        this.proposalLines = event.proposalLines();
        this.proposalSubjects = event.proposalSubjects();
        this.bizNo = event.bizNo();
        this.marketPackageId = event.marketPackageId();
        this.insuranceType = ProposalLine.resolveInsuranceType(event.insuranceType(), event.proposalLines());
        this.tenantId = event.tenantId();
        this.insuredPartyList = event.insuredPartyList();
        this.collectionMode = event.collectionMode();
        this.channelInfo = event.channelInfo();
        this.paymentMode = event.paymentMode();
        this.premiumPaymentYears = event.premiumPaymentYears();
    }

    /**
     * 意向段 → 投保段（渐进精化：意向保额转投保保额，补段ID与投保段状态）。
     * <p>
     * 意向阶段以 {@code lineNo} 表达主附险依附关系，此处转换为段ID引用。意向保费是客户预算或
     * 粗略报价，不能提升为投保段试算保费；实际段保费由承保前的系统试算统一写入。
     * </p>
     * <p>
     * 意向段缺失时（存量事件或单险种意向）回退以 {@code expectedProductCode} 构造单段，
     * 保证转换链路不断。
     * </p>
     *
     * @return 投保段列表
     */
    private List<InsuranceLine> refineToInsuranceLines() {
        // 🔴 G11/g11-04 D4：单据级缴费摘要仅用于「无段列表」历史回退路径与段级缺失时的回退；
        // 有段列表时各主险段**各自解析**（resolveLinePaymentTerms），不得再共用一份。
        LinePaymentTerms documentPaymentTerms = resolveMainPaymentTerms();
        LineCoveragePeriod coveragePeriod = resolveCoveragePeriod();
        if (proposalLines == null || proposalLines.isEmpty()) {
            if (expectedProductCode == null) {
                return List.of();
            }
            String lineId = UUID.randomUUID().toString();
            Money sumInsured = intendedSumInsured != null ? Money.of(intendedSumInsured, CurrencyEnum.CNY.getCode()) : null;
            return List.of(new InsuranceLine(lineId, 1, ProductCategory.MAIN, null, null,
                    expectedProductCode, null, null, insuranceType,
                    sumInsured, null, coveragePeriod, documentPaymentTerms, subjectsForLine(insuranceType, sumInsured),
                    null, null, PolicyLineStatus.UNDERWRITING));
        }
        Map<Integer, String> lineIdByNo = new HashMap<>();
        for (ProposalLine line : proposalLines) {
            lineIdByNo.put(line.lineNo(), UUID.randomUUID().toString());
        }
        List<InsuranceLine> lines = new ArrayList<>();
        for (ProposalLine line : proposalLines) {
            lines.add(new InsuranceLine(lineIdByNo.get(line.lineNo()), line.lineNo(), line.productCategory(),
                    line.parentLineNo() != null ? lineIdByNo.get(line.parentLineNo()) : null, line.productId(),
                    line.productCode(), null, line.productVersion(), line.insuranceType(), line.intendedSumInsured(),
                    null, coveragePeriod, line.productCategory() == ProductCategory.MAIN ? resolveLinePaymentTerms(line) : null,
                    subjectsForLine(line.insuranceType(), line.intendedSumInsured()), null, null,
                    PolicyLineStatus.UNDERWRITING));
        }
        return List.copyOf(lines);
    }

    private LineCoveragePeriod resolveCoveragePeriod() {
        if (insurancePeriodStart == null || insurancePeriodEnd == null) {
            return null;
        }
        return LineCoveragePeriod.fixedTerm(insurancePeriodStart, insurancePeriodEnd, null, null);
    }

    /**
     * 单据级缴费摘要（回退口径）：由事件携带的 {@code paymentMode}/{@code premiumPaymentYears} 解析。
     * <p>
     * 仅用于无段列表的历史回退路径，以及段级缴费条件缺失时的逐段回退。
     * </p>
     */
    private LinePaymentTerms resolveMainPaymentTerms() {
        PaymentFrequency paymentFrequency = BaseEnum.fromCode(PaymentFrequency.class, paymentMode);
        return paymentFrequency != null ? new LinePaymentTerms(paymentFrequency, premiumPaymentYears) : null;
    }

    /**
     * 逐段解析主险段缴费条款（🔴 G11/g11-04 D4：由「一份共用」改「逐段各自解析」）。
     * <p>
     * 段级显式缴费条件优先（意向段携带的出单方案行原值）；段上缺失（存量事件或单险种意向）
     * 回退单据级 {@link #resolveMainPaymentTerms()}。年数缺失按 1 兜底，与直接出单链路的
     * {@code InsuranceLineAssembler.assemblePaymentTerms} 同口径。
     * </p>
     * <p>
     * 🔴 多主险（交强 + 商业同单）下各主险段<b>各自解析</b>——共用一份会在「交强年缴 + 商业分期」时
     * 静默串用，且串用结果随段顺序变化。
     * </p>
     *
     * @param line 主险意向段
     * @return 该段缴费条款；段级与单据级均无有效缴费频率时返回 null
     */
    private LinePaymentTerms resolveLinePaymentTerms(ProposalLine line) {
        if (line.paymentFrequency() == null) {
            return resolveMainPaymentTerms();
        }
        return new LinePaymentTerms(line.paymentFrequency(),
                line.premiumPaymentYears() != null ? line.premiumPaymentYears() : 1);
    }

    /**
     * 人身险未显式声明标的时，以参与方清单中的被保险人生成核保标的。
     * <p>
     * 财产险标的必须由调用方显式提供，不能把被保险人误当成车辆、房屋等财产标的；历史事件缺少
     * 险种分类时同样保持空列表，避免回放时改变既有语义。
     * </p>
     */
    private List<InsuredSubject> personalSubjects(InsuranceProductType lineInsuranceType, Money sumInsured) {
        InsuranceProductType resolvedType = lineInsuranceType != null ? lineInsuranceType : insuranceType;
        if (resolvedType == null || resolvedType.getCategory() != InsuranceCategory.PERSONAL
                || insuredPartyList == null || insuredPartyList.insuredList() == null
                || insuredPartyList.insuredList().isEmpty()) {
            return List.of();
        }

        List<InsuredSubject> subjects = new ArrayList<>();
        for (InsuredPartyList.InsuredInfo insured : insuredPartyList.insuredList()) {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("age", insured.age());
            if (insured.gender() != null) {
                attributes.put("gender", insured.gender().getCode());
            }
            if (insured.relationToHolder() != null && !insured.relationToHolder().isBlank()) {
                attributes.put("relationToHolder", insured.relationToHolder());
            }
            subjects.add(InsuredSubject.ofPerson(UUID.randomUUID().toString(), insured.customerId(), insured.name(),
                    sumInsured, attributes));
        }
        return List.copyOf(subjects);
    }

    /** 人身险沿用参与方兜底，物类险恢复意向阶段携带的标的属性。 */
    private List<InsuredSubject> subjectsForLine(InsuranceProductType lineInsuranceType, Money sumInsured) {
        InsuranceProductType resolvedType = lineInsuranceType != null ? lineInsuranceType : insuranceType;
        if (resolvedType != null && resolvedType.getCategory() == InsuranceCategory.PERSONAL) {
            return personalSubjects(resolvedType, sumInsured);
        }
        if (proposalSubjects == null || proposalSubjects.isEmpty()) {
            return List.of();
        }
        List<InsuredSubject> subjects = new ArrayList<>();
        for (ProposalSubject subject : proposalSubjects) {
            if (subject.subjectType() == null || subject.subjectType().isPersonal()) {
                continue;
            }
            subjects.add(InsuredSubject.ofObject(UUID.randomUUID().toString(), subject.subjectType(),
                    subject.simpleInfo(), sumInsured, subject.attributes()));
        }
        return List.copyOf(subjects);
    }

    /**
     * 【意向单提交】意向单已提交 -> 自动发转换命令，将其状态推进至 CONVERTED_TO_APPLICATION
     */
    @SagaEventHandler(associationProperty = "proposalId")
    public void on(ProposalSubmittedEvent event) {
        inTenantScope(event.tenantId(), event.proposalId(), () -> convertSubmittedProposal(event));
    }

    /**
     * 【意向单提交】意向单已提交 -> 自动发转换命令，将其状态推进至 CONVERTED_TO_APPLICATION。
     * <p>
     * 本步骤自身不经 Feign，但 {@code sendAndWait} 同步触发的后续事件处理（{@code ProposalConvertedEvent}）
     * 会发起段级保额档次取数——出站身份由外层作用域统一提供，故此处同样必须包租户作用域。
     * </p>
     */
    private void convertSubmittedProposal(ProposalSubmittedEvent event) {
        log.info("[ProposalIssuanceSaga] 意向单已提交，自动转投保单: proposalId={}", event.proposalId());
        commandGateway.sendAndWait(ConvertProposalCommand.builder()
                .proposalId(event.proposalId())
                .changeReason("意向单提交后自动转投保单")
                .tenantId(event.tenantId())
                .build(), COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 【意向单转换】Proposal 已 CONVERTED -> 构造投保单命令，创建 Insurance 聚合，结束 Saga
     * <p>
     * InsuranceCreatedEvent 触发 {@code IssuanceSaga} 以 insuranceId 接力完成后续核保/承保/出单流程。
     * </p>
     */
    @EndSaga
    @SagaEventHandler(associationProperty = "proposalId")
    public void on(ProposalConvertedEvent event) {
        inTenantScope(event.tenantId(), event.proposalId(), () -> createInsuranceFromProposal(event));
    }

    /**
     * 【意向单转换】Proposal 已 CONVERTED -> 构造投保单命令，创建 Insurance 聚合，结束 Saga
     * <p>
     * InsuranceCreatedEvent 触发 {@code IssuanceSaga} 以 insuranceId 接力完成后续核保/承保/出单流程。
     * </p>
     * <p>
     * 🔴 本步骤含<b>出站 Feign 调用</b>（{@link ProductSumInsuredTiersLoader#load} 取段级保额档次），
     * 是 g02-06 真机缺陷的第二处命中点，故外层必须已建立租户作用域——见 {@link #inTenantScope}。
     * </p>
     */
    private void createInsuranceFromProposal(ProposalConvertedEvent event) {
        if (tenantId == null || tenantId.isBlank()) {
            log.error("[ProposalIssuanceSaga] Saga 持久化状态缺失，终止不可恢复的转换事件: proposalId={}",
                    event.proposalId());
            return;
        }
        String insuranceId = UUID.randomUUID().toString();
        String insuranceNo = policyNoGenerator.generateInsuranceNo(tenantId);

        List<InsuranceLine> insuranceLines = refineToInsuranceLines();

        ConvertProposalToInsuranceCommand command = ConvertProposalToInsuranceCommand.builder()
                .insuranceId(insuranceId)
                .insuranceNo(insuranceNo)
                .proposalId(event.proposalId())
                .policyForm(policyForm)
                .applicantId(customerId)
                .insuredCount(insuredPartyList != null && insuredPartyList.insuredList() != null
                        ? insuredPartyList.insuredList().size() : 0)
                .exactPremium(null)
                .insurancePeriodStart(insurancePeriodStart)
                .insurancePeriodEnd(insurancePeriodEnd)
                .insuranceLines(insuranceLines)
                .sumInsuredTiers(productSumInsuredTiersLoader.load(insuranceLines))
                .underwritingPriority(0)
                .changeReason("意向单自动转投保单")
                .insuredPartyList(insuredPartyList)
                .insuranceType(insuranceType)
                .collectionMode(collectionMode)
                .channelInfo(channelInfo)
                .bizNo(bizNo)
                .marketPackageId(marketPackageId)
                .tenantId(tenantId)
                .sumInsured(intendedSumInsured)
                .paymentMode(paymentMode)
                .premiumPaymentYears(premiumPaymentYears)
                .build();

        commandGateway.sendAndWait(command, COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        commandGateway.sendAndWait(new SubmitUnderwritingCommand(insuranceId, tenantId), COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        log.info("[ProposalIssuanceSaga] 投保单已创建，后续由 IssuanceSaga 接力: proposalId={}, insuranceId={}",
                event.proposalId(), insuranceId);
    }

    /**
     * 【意向单作废】提前结束 Saga，无后续流程
     */
    @EndSaga
    @SagaEventHandler(associationProperty = "proposalId")
    public void on(ProposalVoidedEvent event) {
        inTenantScope(event.tenantId(), event.proposalId(), () -> abandonVoidedProposal(event));
    }

    /** 【意向单作废】提前结束 Saga，无后续流程。 */
    private void abandonVoidedProposal(ProposalVoidedEvent event) {
        log.warn("[ProposalIssuanceSaga] 意向单已作废，结束 Saga: proposalId={}", event.proposalId());
        SagaLifecycle.end();
    }

    /**
     * 在事件携带的租户作用域内执行 Saga 步骤。
     * <p>
     * 🔴 <b>成因（2026-09-27 g02-06 真机实测）</b>：Saga 事件处理器运行在 Axon 的 tracking
     * processor 线程上，与创建 Saga 的 Web 请求线程不是同一个线程；{@code RequestContextHolder}
     * 基于 {@code ThreadLocal}，上下文不跨线程传播。本类步骤的出站调用（{@link ProductSumInsuredTiersLoader}
     * 取段级保额档次）依赖线程上下文提供租户——出站拦截器是「上下文有才注入」策略，上下文为空时
     * <b>静默</b>不加 {@code X-Tenant-Id}，对端契约入口随即 400（{@code TENANT_HEADER_MISSING}），
     * 且取数器「远程失败不兜底」会把异常抛给 Saga，使该次出单挂起重试。
     * </p>
     * <p>
     * 故每个 {@code @SagaEventHandler} 的<b>首条语句</b>一律调用本方法，由结构守卫
     * {@code SagaTenantScopeGuardTest} 机械保证。
     * </p>
     *
     * @param tenantId   事件携带的租户ID
     * @param proposalId 意向单号（仅用于告警定位）
     * @param step       在该租户作用域内执行的步骤
     */
    private void inTenantScope(String tenantId, String proposalId, Runnable step) {
        if (tenantId == null || tenantId.isBlank()) {
            // 保持改造前行为（不建立作用域），但留下可检索告警——静默降级会让「出站缺租户头」无从反查
            log.warn("[ProposalIssuanceSaga] 事件未携带租户，跳过身份作用域建立（本步骤出站调用将缺少租户头）: proposalId={}",
                    proposalId);
            step.run();
            return;
        }
        RequestContextHolder.runAsTenant(tenantId, step);
    }
}
