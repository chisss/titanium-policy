package com.titanium.policy.application.saga;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
import com.titanium.metadata.enums.billing.PremiumCollectionMode;
import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.application.orchestration.issuance.assembler.PolicyProductAssembler;
import com.titanium.policy.application.orchestration.issuance.orchestrator.PremiumCollectionOrchestrator;
import com.titanium.policy.application.orchestration.issuance.orchestrator.PremiumScheduleOrchestrator;
import com.titanium.policy.application.orchestration.issuance.service.InsuranceLinePremiumConfirmationService;
import com.titanium.policy.application.orchestration.issuance.service.InsuranceLinePremiumConfirmationService.ConfirmationSummary;
import com.titanium.policy.command.ActivatePolicyCommand;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.command.LinkInvestmentAccountCommand;
import com.titanium.policy.command.ReceiveUnderwritingResultCommand;
import com.titanium.policy.command.TriggerIssuanceCommand;
import com.titanium.policy.common.constant.PolicyConstants;
import com.titanium.policy.common.constant.PolicyConstants.LineExtendDataKey;
import com.titanium.policy.entity.insurance.InsuranceLine;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.entity.insurance.InsuredPartyList.InsuredInfo;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.event.insurance.InsuranceCreatedEvent;
import com.titanium.policy.event.insurance.InsuranceIssuedEvent;
import com.titanium.policy.event.insurance.InsuranceSubmittedForUnderwritingEvent;
import com.titanium.policy.event.insurance.UnderwritingResultReceivedEvent;
import com.titanium.policy.generator.PolicyNoGenerator;
import com.titanium.policy.port.investment.InvestmentAccountPort;
import com.titanium.policy.port.product.ProductServicePort;
import com.titanium.policy.port.underwriting.UnderwritingDecisionGateway;
import com.titanium.policy.service.PolicyIssuanceDomainService;
import com.titanium.policy.valueobject.insurance.UnderwritingDecisionRequest;
import com.titanium.policy.valueobject.insurance.UnderwritingResult;
import com.titanium.policy.valueobject.issuance.HealthDeclarationInput;
import com.titanium.policy.valueobject.policy.ChannelInfo;
import com.titanium.policy.valueobject.policy.CollectionInfo;
import com.titanium.policy.valueobject.policy.CollectionResult;
import com.titanium.policy.valueobject.policy.PolicyPeriod;

import lombok.extern.slf4j.Slf4j;

/**
 * 投保出单 Saga（投保 → 核保 → 承保 → 出单 全链路编排）
 * <p>
 * 以 {@code insuranceId} 为关联键，串联投保单生命周期的跨步骤长事务，将原先散落在
 * 应用服务/编排器中的手工串联收敛为单一过程管理器（Process Manager）：
 * </p>
 * <ol>
 *     <li><b>投保</b>：{@link InsuranceCreatedEvent} 启动 Saga，记忆构建保单所需数据；</li>
 *     <li><b>核保</b>：{@link InsuranceSubmittedForUnderwritingEvent} 触发，经 {@link UnderwritingDecisionGateway}
 *         向核保域请求核保结论，并以 {@link ReceiveUnderwritingResultCommand} 将结论回写投保单聚合；</li>
 *     <li><b>承保</b>：{@link UnderwritingResultReceivedEvent} 触发，核保通过则发 {@link TriggerIssuanceCommand}
 *         推进承保出单；核保拒绝/暂缓则结束 Saga（暂缓需人工介入后另行驱动）；</li>
 *     <li><b>出单</b>：{@link InsuranceIssuedEvent} 触发，用记忆的数据发 {@link CreatePolicyCommand}
 *         创建正式保单并结束 Saga。</li>
 * </ol>
 * <p>
 * <b>核保通信（双轨回流，两轨均已落地）</b>：核保结论经两条独立通道抵达本 Saga，二者<b>不分场景</b>、
 * 同一决策可能两轨都到——核保域 {@code UnderwritingKafkaEventPublisher} 对每个
 * {@code UnderwritingDecidedEvent} <b>无条件</b>发布，自动决策件在同步轨即时返回的同时也已进入异步轨：
 * </p>
 * <ul>
 *     <li><b>同步轨</b>：本 Saga 第 2 步经 {@link UnderwritingDecisionGateway} 即时拉取结论，以
 *         {@link ReceiveUnderwritingResultCommand} 回写（见 {@code on(InsuranceSubmittedForUnderwritingEvent)}）；</li>
 *     <li><b>异步轨</b>：核保域发布到 {@code underwriting-decided}，由 policy 域
 *         {@code UnderwritingDecidedEventListener}（infrastructure/messaging）消费后回写同一命令。</li>
 * </ul>
 * <p>
 * 两轨收敛到<b>同一个命令</b>，故第 3 步（{@link UnderwritingResultReceivedEvent} → 承保）对来源无感；
 * 重复投递的去重由聚合根承担（{@code Insurance.isRedeliveredResult}：同核保单号 + 同结论 ⇒ 静默幂等、
 * 不发事件），守卫测试见 {@code InsuranceUnderwritingResultIdempotencyTest} 与
 * {@code UnderwritingDecidedEventListenerTest}。
 * </p>
 */
@Slf4j
@Saga
@JsonAutoDetect(fieldVisibility = Visibility.ANY, getterVisibility = Visibility.NONE,
        isGetterVisibility = Visibility.NONE)
public class IssuanceSaga {


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

    @Autowired
    private transient UnderwritingDecisionGateway underwritingDecisionGateway;

    @Autowired
    private transient PolicyIssuanceDomainService policyIssuanceDomainService;

    @Autowired
    private transient InvestmentAccountPort investmentAccountPort;

    /** 产品服务端口（G14/g14-04a：开户前取管理费费率——产品定价配置是费率的唯一权威来源） */
    @Autowired
    private transient ProductServicePort productServicePort;

    @Autowired
    private transient PolicyProductAssembler policyProductAssembler;

    @Autowired
    private transient InsuranceLinePremiumConfirmationService premiumConfirmationService;

    @Autowired
    private transient PremiumCollectionOrchestrator premiumCollectionOrchestrator;

    @Autowired
    private transient PremiumScheduleOrchestrator premiumScheduleOrchestrator;

    /** 保单形态 */
    private PolicyForm    policyForm;
    /** 投保人ID */
    private String        holderId;
    /** 产品ID（取投保险种编码列表首个，承保载体，透传签发事件供下游采集/分保） */
    private String        productId;
    /** 精确保费（兜底：billing 计算失败时使用） */
    private BigDecimal    exactPremium;
    /** 保障起期 */
    private LocalDateTime insurancePeriodStart;
    /** 保障止期 */
    private LocalDateTime insurancePeriodEnd;
    /** 租户ID */
    private String        tenantId;
    /** 险种三级分类（自投保单事件记忆，出单时透传保单，可空以兼容存量事件） */
    private InsuranceProductType insuranceType;
    /** 参与方清单（自投保单事件记忆，出单时透传保单，含投保人/被保险人/受益人快照） */
    private InsuredPartyList insuredPartyList;
    /** 核保加费率（UW-3：核保结果回传的结构化加费率，出单时并入保费；无加费为 null） */
    private BigDecimal extraPremiumRatio;
    /** 基本保额（BILL-2：供 billing 计算真实保费，null 则回退 exactPremium） */
    private BigDecimal sumInsured;
    /** 缴费模式 code（BILL-2：供 billing 计算） */
    private String paymentMode;
    /** 缴费年数（BILL-2：供 billing 精算计算，0 表示未知） */
    private int premiumPaymentYears;
    /** 投保险种段列表（一单多险载体，承保时精化为保单段并冻结条款责任快照） */
    private List<InsuranceLine> insuranceLines;
    /** 关联意向单ID（三步出单来源，透传保单实现三级贯通） */
    private String proposalId;
    /** 核保单ID（承保依据溯源） */
    private String underwritingId;
    /** 营销包ID（弱引用，透传保单供转化统计） */
    private String marketPackageId;
    /** 出单业务流水号（透传保单供进度回写） */
    private String bizNo;
    /** 收费方式（出单期确定，决定保单生效是否以收讫为前提） */
    private PremiumCollectionMode collectionMode;
    /** 渠道信息（透传保单，此前保单查不到来源渠道） */
    private ChannelInfo channelInfo;

    /**
     * 【投保】投保单创建 → 启动 Saga，记忆后续创建保单所需数据
     */
    @StartSaga
    @SagaEventHandler(associationProperty = "insuranceId")
    public void on(InsuranceCreatedEvent event) {
        inTenantScope(event.tenantId(), event.insuranceId(), () -> rememberCreation(event));
    }

    /** 【投保】记忆后续创建保单所需数据。 */
    private void rememberCreation(InsuranceCreatedEvent event) {
        log.info("[IssuanceSaga] 启动: insuranceId={}, tenantId={}", event.insuranceId(), event.tenantId());
        this.policyForm = event.policyForm();
        this.holderId = event.holderId();
        // 主险产品ID必须取结构化险种段；产品编码不是产品主键，不能用于远程取数或保单冗余字段。
        InsuranceLine mainLine = event.mainLine();
        this.productId = mainLine != null ? mainLine.productId() : null;
        this.exactPremium = event.exactPremium();
        this.insurancePeriodStart = event.insurancePeriodStart();
        this.insurancePeriodEnd = event.insurancePeriodEnd();
        this.tenantId = event.tenantId();
        this.insuranceType = event.insuranceType();
        // 记忆参与方清单快照，出单时透传正式保单
        this.insuredPartyList = event.insuredPartyList();
        // BILL-2：记忆保额/缴费模式，出单时调 billing 计算真实保费
        this.sumInsured = event.sumInsured();
        this.paymentMode = event.paymentMode();
        this.premiumPaymentYears = event.premiumPaymentYears();
        // 记忆险种段（一单多险载体）与出单期确定的收费/渠道/溯源信息，出单时透传保单
        this.insuranceLines = event.insuranceLines();
        this.proposalId = event.proposalId();
        this.marketPackageId = event.marketPackageId();
        this.bizNo = event.bizNo();
        this.collectionMode = event.collectionMode();
        this.channelInfo = event.channelInfo();
    }

    /**
     * 【核保】投保单提交核保 → 请求核保域出具结论，并将结论回写投保单聚合
     */
    @SagaEventHandler(associationProperty = "insuranceId")
    public void on(InsuranceSubmittedForUnderwritingEvent event) {
        inTenantScope(event.tenantId(), event.insuranceId(), () -> requestUnderwritingDecision(event));
    }

    /** 【核保】请求核保域出具结论并回写投保单聚合。 */
    private void requestUnderwritingDecision(InsuranceSubmittedForUnderwritingEvent event) {
        log.info("[IssuanceSaga] 提交核保，请求核保结论: insuranceId={}", event.insuranceId());

        // UW-2：从记忆的参与方清单提取首要被保人核保要素（年龄/性别），构建富核保请求。
        // G02/AC-01：年龄/性别现已经跨域契约全量透传；职业类别/BMI 在承保前置链路无数据源，
        // 缺失一律留痕（见 warnMissingRiskFactors），核保域对 null 要素按其可得要素评分。
        InsuredInfo primaryInsured = extractPrimaryInsured();
        warnMissingRiskFactors(primaryInsured, event.insuranceId());
        // G12/g12-01 AC-01：从记忆的险种段 extendData 提取健康告知，随本请求透传核保域（未提供/不完整留痕）
        HealthDeclarationInput healthDeclaration = extractHealthDeclaration(event.insuranceId());
        UnderwritingDecisionRequest request = new UnderwritingDecisionRequest(event.insuranceId(), event.holderId(),
                event.insuredCount(), this.sumInsured, event.exactPremium(), event.currency(), event.productCodes(),
                event.tenantId(), primaryInsured != null ? primaryInsured.age() : null,
                primaryInsured != null ? primaryInsured.gender() : null,
                null, null, healthDeclaration);
        UnderwritingResult result = underwritingDecisionGateway.requestDecision(request);

        commandGateway.sendAndWait(new ReceiveUnderwritingResultCommand(event.insuranceId(), result, event.tenantId()), COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        log.info("[IssuanceSaga] 核保结论已回写投保单: insuranceId={}, 结论={}, 加费率={}", event.insuranceId(),
                result.resultCode(), result.extraPremiumRatio());
    }

    /**
     * 【承保】核保结果接收 → 核保通过推进承保出单；拒绝/暂缓结束 Saga
     */
    /**
     * 【承保】核保结果接收 → 核保通过推进承保出单；拒绝/暂缓结束 Saga
     * <p>
     * 「哪些核保结论算通过」的<b>承保准入规则</b>委托 {@link PolicyIssuanceDomainService#canIssueByConclusion}
     * 裁决，Saga 本身只依裁决结果做<b>流程路由</b>（通过则发命令推进，否则结束流程），不含业务判断。
     * </p>
     */
    @SagaEventHandler(associationProperty = "insuranceId")
    public void on(UnderwritingResultReceivedEvent event) {
        inTenantScope(event.tenantId(), event.insuranceId(), () -> applyUnderwritingResult(event));
    }

    /** 【承保】按核保结论路由：通过则推进出单，否则结束 Saga。 */
    private void applyUnderwritingResult(UnderwritingResultReceivedEvent event) {
        // UW-3：记忆核保加费率，出单时并入保费
        this.extraPremiumRatio = event.extraPremiumRatio();
        // 记忆核保单ID，出单时透传保单（承保依据溯源，此前保单查不到核保单）
        this.underwritingId = event.underwritingId();
        // 段级结论回写：整单结论下发各段，拒保段保费不计入总保费
        if (this.insuranceLines != null) {
            List<InsuranceLine> updated = new ArrayList<>();
            for (InsuranceLine line : this.insuranceLines) {
                updated.add(line.withUnderwritingResult(event.resultCode(), event.extraPremiumRatio()));
            }
            this.insuranceLines = updated;
        }

        // 以核保事件构造核保结果值对象，交领域服务裁决承保准入（业务规则不落在 Saga）
        UnderwritingResult result = new UnderwritingResult(event.underwritingId(), event.resultCode(), event.opinion(),
                event.underwriterId(), event.underwritingTime(), event.underwritingCondition(),
                event.extraPremiumRatio());

        if (policyIssuanceDomainService.canIssueByConclusion(result)) {
            log.info("[IssuanceSaga] 核保通过，触发承保出单: insuranceId={}", event.insuranceId());
            commandGateway.sendAndWait(new TriggerIssuanceCommand(event.insuranceId(), event.tenantId()), COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } else {
            log.warn("[IssuanceSaga] 核保未通过（{}），结束 Saga: insuranceId={}", event.resultCode(), event.insuranceId());
            SagaLifecycle.end();
        }
    }

    /**
     * 【出单】投保单承保出单 → 创建正式保单，结束 Saga
     */
    @EndSaga
    @SagaEventHandler(associationProperty = "insuranceId")
    public void on(InsuranceIssuedEvent event) {
        inTenantScope(event.tenantId(), event.insuranceId(), () -> issuePolicy(event));
    }

    /** 【出单】创建正式保单，并完成出单后收费/期缴/投资账户编排。 */
    private void issuePolicy(InsuranceIssuedEvent event) {
        String policyId = UUID.randomUUID().toString();
        String policyNo = policyNoGenerator.generatePolicyNo(tenantId);

        String confirmationBizNo = bizNo != null && !bizNo.isBlank() ? bizNo : event.insuranceId();
        ConfirmationSummary confirmation = premiumConfirmationService.confirm(
                insuranceLines, insuredPartyList, event.insuranceId(), confirmationBizNo,
                insurancePeriodStart, tenantId, channelInfo != null ? channelInfo.channelId() : null, 1, true);
        Money standardPremium = confirmation.standardPremium();
        Money payablePremium = confirmation.totalPremium();
        List<PolicyProduct> policyProducts = policyProductAssembler.assembleFromInsuranceLines(
                confirmation.lines(), tenantId, confirmation.calculationReferences());

        CreatePolicyCommand command = new CreatePolicyCommand(
                policyId,
                policyNo,
                event.insuranceId(),
                proposalId,
                underwritingId,
                bizNo,
                marketPackageId,
                policyForm,
                productId,
                null,
                insuredPartyList,
                policyProducts,
                sumInsured != null ? Money.of(sumInsured, payablePremium.currency()) : null,
                standardPremium,
                payablePremium,
                PolicyPeriod.of(insurancePeriodStart, insurancePeriodEnd),
                null,
                CollectionInfo.initial(collectionMode, payablePremium, LocalDateTime.now()),
                channelInfo,
                insuranceType,
                tenantId);

        commandGateway.sendAndWait(command, COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        log.info(
                "[IssuanceSaga] 承保出单完成，已创建保单: insuranceId={}, policyId={}, policyNo={}, 标准保费={}, 加费率={}, 最终保费={}",
                event.insuranceId(), policyId, policyNo, standardPremium.value(), extraPremiumRatio,
                payablePremium.value());

        // 出单后收费：按收费方式路由（开账单 → 建支付单 → 收讫回写），取代此前的「只开账单不收钱」。
        // 收费失败不销毁保单——保单停未生效、账单待催缴，可重新发起收款。
        CollectionResult collection = null;
        try {
            collection = premiumCollectionOrchestrator.collect(policyId, holderId, payablePremium,
                    collectionMode, insurancePeriodStart != null ? insurancePeriodStart.toLocalDate() : null,
                    tenantId, confirmation.calculationReferences());
            log.info("[IssuanceSaga] 收费编排完成: policyId={}, 收费方式={}, 收讫状态={}, billId={}, 支付单={}",
                    policyId, collectionMode, collection.status(), collection.billId(),
                    collection.paymentOrderId());
            // 已收讫（免支付）或后付挂账（先享后付）时保单即刻满足保费条件，驱动生效；
            // 其余方式待收费回调经 PremiumCollectionSaga 驱动。
            if (collection.allowsActivation()) {
                activateIfPeriodStarted(policyId);
            }
        } catch (Exception ex) {
            log.error("[IssuanceSaga] 收费编排异常（不阻断出单，保单停未生效待催缴）: policyId={}", policyId, ex);
        }

        // 账单成功后生成期缴计划；失败由编排器隔离，不回滚已创建的保单与账单。
        premiumScheduleOrchestrator.generate(policyId, collection != null ? collection.billId() : null,
                collection != null ? collection.billingAccountId() : null, paymentMode, premiumPaymentYears,
                payablePremium,
                insurancePeriodStart != null ? insurancePeriodStart.toLocalDate() : LocalDateTime.now().toLocalDate(),
                tenantId);

        // 投连/万能保单：出单后经 InvestmentAccountPort 开立投资账户并挂接到保单（账户失败不阻断出单，待补偿）
        if (policyForm != null && policyForm.isInvestmentLinked()) {
            try {
                // G14/g14-04a：费率取产品定价配置（唯一权威来源，替代 policy 侧原 0.015 硬编码兜底）。
                // 取数失败抛 RemoteDependencyUnavailableException 由下方 catch 捕获 ⇒ 本期不建账户、待补偿：
                // 「无账户（可见的缺失）」优于「有账户但费率被静默折叠」（后者每月计提静默跳过、事后无从对账）。
                // null=产品未配置（合法不收费）原样透传。
                BigDecimal managementFeeRate = productServicePort.getManagementFeeRate(productId);
                String accountId = investmentAccountPort.openAccount(policyId, policyForm, payablePremium,
                        managementFeeRate);
                if (accountId != null) {
                    commandGateway.sendAndWait(
                            new LinkInvestmentAccountCommand(policyId, accountId, PolicyConstants.POLICY_SYSTEM,
                                    tenantId), COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    log.info("[IssuanceSaga] 投连/万能保单已挂接投资账户: policyId={}, accountId={}", policyId,
                            accountId);
                } else {
                    log.error("[IssuanceSaga] 开立投资账户返回空（不阻断出单，待补偿）: policyId={}", policyId);
                }
            } catch (Exception ex) {
                log.error("[IssuanceSaga] 开立/挂接投资账户失败（不阻断出单，待补偿）: policyId={}", policyId, ex);
            }
        }
    }

    /**
     * BILL-2：调 billing 计算真实标准保费。
     * <p>
     * 有 sumInsured + productId 时发起远程保费计算；任一缺失或远程失败时回退 exactPremium，
     * 保证出单不因保费计算失败而中断。
     * </p>
     */
    /**
     * 保费条件已满足时驱动保单生效（保障起期未到则由定时任务后续激活）。
     * <p>
     * 生效的两个前提——保费条件与保障起期——均由聚合内守护（{@code CollectionInfo} 与
     * {@code PolicyPeriod}）。此处仅在收费条件满足时尝试激活；起期未到时聚合会拒绝，
     * 属预期路径故仅记日志不视为异常。
     * </p>
     */
    private void activateIfPeriodStarted(String policyId) {
        try {
            commandGateway.sendAndWait(new ActivatePolicyCommand(policyId, tenantId), COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("[IssuanceSaga] 保费条件已满足，保单已生效: policyId={}", policyId);
        } catch (Exception ex) {
            log.info("[IssuanceSaga] 保单暂不可生效（保障起期未到，待定时任务激活）: policyId={}, 原因={}", policyId,
                    ex.getMessage());
        }
    }

    /**
     * 提取首要被保人（参与方清单被保险人列表首个），供富核保请求填充年龄/性别等风险要素。
     *
     * @return 首要被保人信息；清单缺失或无被保人时返回 null
     */
    private InsuredInfo extractPrimaryInsured() {
        if (insuredPartyList == null || insuredPartyList.insuredList() == null
                || insuredPartyList.insuredList().isEmpty()) {
            return null;
        }
        return insuredPartyList.insuredList().get(0);
    }

    /**
     * 提取健康告知（G12/g12-01 AC-01）
     * <p>
     * 健康告知随出单请求的段级 {@code extendData} 提交（键契约
     * {@link LineExtendDataKey#HEALTH_DECLARATION}），本方法遍历记忆的险种段，取<b>首个携带告知块的段</b>
     * 解析——首个携带段即权威，其解析结果（含「声明不完整」的 null）即本单结论，不再看后续段
     * （一单内多处告知属上游数据异常，逐段兜底会让结论不确定）。解析口径（三个必答项齐全且身高体重为正）
     * 见 {@link HealthDeclarationInput#fromExtendData}。
     * </p>
     * <p>
     * 🔴 <b>缺失与不完整分列告警，且一律不代填</b>：① 全部段均无告知块 → 按「未提供」告警；
     * ② 有告知块但三个必答项（吸烟/身高/体重）缺任一项或非法 → 按「声明不完整」告警，同样按未提供处理。
     * 两种成因反查方向不同（前端未采集 vs 采集残缺），故分开留痕；静默或以默认值代填会把
     * 「没告知」变成「低风险告知」，核保域将据此作出错误决策。
     * </p>
     *
     * @param insuranceId 投保单ID（缺失留痕的检索键）
     * @return 完整健康告知；未提供或声明不完整时返回 null
     */
    private HealthDeclarationInput extractHealthDeclaration(String insuranceId) {
        if (insuranceLines != null) {
            for (InsuranceLine line : insuranceLines) {
                Map<String, Object> extendData = line.extendData();
                if (extendData == null || !extendData.containsKey(LineExtendDataKey.HEALTH_DECLARATION)) {
                    continue;
                }
                HealthDeclarationInput declaration = HealthDeclarationInput.fromExtendData(extendData);
                if (declaration == null) {
                    log.warn("[IssuanceSaga] 健康告知声明不完整（吸烟/身高/体重缺项或非法），按未提供处理:"
                            + " insuranceId={}, lineNo={}", insuranceId, line.lineNo());
                }
                return declaration;
            }
        }
        log.warn("[IssuanceSaga] 出单请求未携带健康告知，本次核保不含健康告知要素: insuranceId={}", insuranceId);
        return null;
    }

    /**
     * 核保要素缺失留痕（G02/AC-01）
     * <p>
     * 🔴 缺失<b>不阻断</b>出单——核保域对未提供的要素按 {@code null} 处理、以其余可得要素评分
     * （{@code InsuredRiskFactors} 四分量均可空）。但缺失必须<b>可检索</b>：静默降级会让
     * 「某险种长期在零要素下自动核保」无从发现，而这正是改造前「职业+BMI 从未真正到达核保域」
     * 潜伏至今的原因——链路两端各自都「正常」，中间丢要素无声无息。
     * </p>
     * <p>
     * 两类缺失成因不同，分列告警以便反查：① 被保人参与方清单缺失（异常路径，年龄/性别一并丢失）；
     * ② 职业类别/BMI 结构性不可得（{@code InsuredPartyList.InsuredInfo} 无对应字段，属柜面/APP
     * 完整录入才有的人力要素，承保前置链路本就不具备该数据源）。
     * </p>
     *
     * @param primaryInsured 首要被保人（可能为 null）
     * @param insuranceId    投保单ID（缺失留痕的检索键）
     */
    private void warnMissingRiskFactors(InsuredInfo primaryInsured, String insuranceId) {
        if (primaryInsured == null) {
            log.warn("[IssuanceSaga] 被保人参与方清单缺失，年龄/性别无法透传核保域: insuranceId={}", insuranceId);
        }
        log.warn("[IssuanceSaga] 职业类别/BMI 在承保前置链路不可得（参与方清单无对应字段），本次核保不含该两项要素:"
                + " insuranceId={}", insuranceId);
    }

    /**
     * 在租户请求上下文作用域内执行 Saga 步骤。
     * <p>
     * 🔴 <b>为何 Saga 必须自建作用域</b>：{@code RequestContextHolder} 基于 {@code ThreadLocal}，
     * 而 Saga 的事件处理器运行在 Axon 的 tracking processor 线程上——它与创建 Saga 的 Web 请求线程
     * 不是同一个线程，线程上下文不跨线程传播。本类的出站调用（核保 Feign、产品试算 Feign、billing
     * Feign）与命令派发都<b>依赖线程上下文提供租户</b>：出站 Feign 拦截器是「上下文有才注入」策略，
     * 上下文为空时静默不加 {@code X-Tenant-Id} 头，对端契约入口随即 400（{@code TENANT_HEADER_MISSING}）。
     * </p>
     * <p>
     * 2026-09-27 g02-06 真机实测：第 4 步出单调用产品试算即因该 400 失败，Saga 处理器进入退避重试、
     * 阻塞整个 tracking segment。根因不是某处漏传租户（各调用点的 {@code tenantId} 一直在显式传参），
     * 而是 <b>Saga 线程根本没有上下文</b>——参数传得再全，也到不了出站请求头。
     * </p>
     * <p>
     * 故此处以 Saga <b>自身持久化的租户</b>（取自事件载荷、随 Saga 状态一同序列化）建立作用域，
     * 不依赖任何跨线程上下文搬运机制；这是 {@code RequestContextHolder.runAsTenant} 的既定用法
     * （「非 Web 线程的唯一推荐用法」）。作用域在步骤结束后自动还原，不污染线程池复用线程。
     * </p>
     *
     * @param tenantId    本步骤所属租户
     * @param insuranceId 投保单ID（缺失租户时的告警检索键）
     * @param step        该步骤的处理逻辑
     */
    private void inTenantScope(String tenantId, String insuranceId, Runnable step) {
        if (tenantId == null || tenantId.isBlank()) {
            // 保持改造前行为（不建立作用域），但留下可检索告警——静默降级会让「出站缺租户头」无从反查
            log.warn("[IssuanceSaga] 事件未携带租户，跳过身份作用域建立（本步骤出站调用将缺少租户头）: insuranceId={}",
                    insuranceId);
            step.run();
            return;
        }
        RequestContextHolder.runAsTenant(tenantId, step);
    }

}
