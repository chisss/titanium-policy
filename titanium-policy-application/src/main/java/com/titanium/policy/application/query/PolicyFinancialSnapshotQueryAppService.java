package com.titanium.policy.application.query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.axonframework.messaging.unitofwork.DefaultUnitOfWork;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.axonframework.modelling.command.Repository;
import org.springframework.stereotype.Service;

import com.titanium.metadata.enums.billing.BillingEnum.ReconciliationStatus;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.exception.DomainException;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.aggregate.Policy;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.port.billing.BillingServicePort;
import com.titanium.policy.port.product.PolicyCashValuePort;
import com.titanium.policy.repository.PolicyLoanAccountRepository;
import com.titanium.policy.valueobject.billing.UnpaidBill;
import com.titanium.policy.valueobject.financial.PolicyFinancialSnapshot;
import com.titanium.policy.valueobject.loan.PolicyLoanAccount;
import com.titanium.policy.valueobject.policy.LinePaymentTerms;
import com.titanium.policy.valueobject.product.PolicyCashValue;

import lombok.RequiredArgsConstructor;

/**
 * 保单财务快照查询应用服务（application/query 读入口，g04-02）。
 * <p>
 * 保全域退费/贷款/减额缴清等现金价值类动作的取数底座。职责是「保单号 → 财务快照」的
 * <b>写侧强一致</b>翻译编排，取数面与口径如下：
 * </p>
 * <ul>
 *   <li><b>保单要素</b>：经 {@link Repository} 重建 {@link Policy} 聚合取得
 *       （事件流强一致，不读最终一致的读模型）；</li>
 *   <li><b>原保额</b>：取主险段基本保额（{@code policy.coverage.sumInsured} 执行器同源口径），
 *       供减额缴清核定「新保额 ≤ 原保额」；同一主险段的<b>标识</b>（{@code policyProductId}）随同批下发——
 *       它正是减额缴清回写该字段时的 {@code objectId}，二者必须来自同一次取数（见 {@code requireMainSegment}）；</li>
 *   <li><b>现金价值</b>：转调产品域退保价值策略（{@link PolicyCashValuePort}），保单年度口径
 *       与现金价值查询服务共用（生效日至估值日年差 + 1），消费方不得自行推导；</li>
 *   <li><b>欠费</b>：转调计费域账单（{@link BillingServicePort#getUnpaidBills}），口径 =
 *       未结账单未缴金额合计；</li>
 *   <li><b>贷款未偿本息</b>：经 {@link PolicyLoanAccountRepository} 重建贷款账户聚合
 *       （事件流强一致），未偿本金/未偿利息均取聚合派生值（单利 365 分段计息口径由聚合独担，
 *       本层只汇总，消费方不得自行推导）；</li>
 *   <li><b>已缴期数</b>：缴费流水中对账成功（RECONCILED）的笔数。</li>
 *   <li><b>退费口径四项</b>（g05-02）：出单流水号 / 年缴保费 / 保障止期 / 缴费是否期满，供保全域
 *       退保退费按未满期保费基准折算。年保费与期满判定的口径均落在本层与
 *       {@link LinePaymentTerms}，消费方不得自行还原。</li>
 * </ul>
 * <p>
 * 🔴 <b>失败关闭</b>：保单不存在 / 租户不符 → {@link PolicyErrorCode#POLICY_NOT_EXIST}；
 * 任一财务要素缺失（生效日/产品/保费/贷款利率）或外部域取数失败 →
 * {@link PolicyErrorCode#POLICY_FINANCIAL_DATA_MISSING}。禁止 {@code orElse(ZERO)} 兜底——
 * 「无记录 = 0」（无未结贷款、无欠费、无红利、无投资账户）是业务事实，与「取不到」严格区分。
 * </p>
 * <p>
 * 🔴 <b>退费口径四项是可空项，不参与上述失败关闭</b>：贷款与减额缴清路径不读它们，若因其缺失而拒绝
 * 出快照，会让这两条与缴费条件无关的链路被连坐。缺失的语义与拒绝责任由使用点承担（退保在未满期
 * 保费基准下须拒绝按 0 退，见 {@code SurrenderPremiumFacts}）。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class PolicyFinancialSnapshotQueryAppService {

    private final Repository<Policy> policyRepository;
    private final PolicyCashValuePort policyCashValuePort;
    private final BillingServicePort billingServicePort;
    private final PolicyLoanAccountRepository policyLoanAccountRepository;

    /**
     * 查保单财务快照（强一致）。
     *
     * @param tenantId 租户ID
     * @param policyId 保单ID
     * @return 财务快照；缺数据一律抛领域异常（失败关闭），绝不返回空
     */
    public PolicyFinancialSnapshot query(String tenantId, String policyId) {
        Policy policy = loadPolicy(tenantId, policyId);
        LocalDate valuationDate = LocalDate.now();

        BigDecimal cashValue = resolveCashValue(tenantId, policy, valuationDate);
        BigDecimal loanOutstandingPrincipal = resolveLoanOutstandingPrincipal(tenantId, policyId);
        BigDecimal loanOutstandingInterest = resolveLoanOutstandingInterest(tenantId, policyId);
        BigDecimal arrearsAmount = resolveArrearsAmount(policyId);
        PolicyProduct mainSegment = requireMainSegment(policy);
        int paidInstallments = paidInstallmentsOf(policy);

        return new PolicyFinancialSnapshot(
                cashValue,
                sumInsuredOf(mainSegment),
                mainSegmentIdOf(mainSegment),
                loanOutstandingPrincipal,
                loanOutstandingInterest,
                arrearsAmount,
                dividendBalanceOf(policy),
                accountValueOf(policy),
                effectiveDateOf(policy),
                paidInstallments,
                issuanceBizNoOf(policy),
                annualPremiumOf(policy),
                coverageEndDateOf(policy),
                premiumPaidUpOf(policy, paidInstallments));
    }

    /**
     * 经事件流重建聚合并做租户校验。
     * <p>
     * 不存在与「不属于本租户」同形拒绝（{@code POLICY_NOT_EXIST}），不泄漏资源存在性。
     * </p>
     */
    private Policy loadPolicy(String tenantId, String policyId) {
        // ES 仓储 load 要求线程内有 UnitOfWork：查询路径无命令上下文，须手工开启，提取聚合根后
        // 回滚（只读用途，不产生事件副作用，同时释放聚合锁）。
        UnitOfWork<?> unitOfWork = DefaultUnitOfWork.startAndGet(null);
        try {
            // load 返回锁感知包装，经 invoke 提取聚合根（只读用途，不产生命令副作用）
            Policy policy = policyRepository.load(policyId).invoke(Function.identity());
            if (!Objects.equals(tenantId, policy.getTenantId())) {
                throw new DomainException(PolicyErrorCode.POLICY_NOT_EXIST, "保单不存在: " + policyId);
            }
            return policy;
        } catch (AggregateNotFoundException exception) {
            throw new DomainException(PolicyErrorCode.POLICY_NOT_EXIST, "保单不存在: " + policyId);
        } finally {
            unitOfWork.rollback();
        }
    }

    /**
     * 现金价值：保单要素（产品/保费/生效日）任一缺失，或产品域无适用策略 → 失败关闭。
     * <p>
     * 保单年度口径与 {@link PolicyCashValueQueryAppService} 共用（生效日至估值日年差 + 1，
     * 下界收敛到 1）。
     * </p>
     */
    private BigDecimal resolveCashValue(String tenantId, Policy policy, LocalDate valuationDate) {
        LocalDate effectiveDate = effectiveDateOf(policy);
        if (policy.getProductId() == null || policy.getTotalPremium() == null
                || policy.getTotalPremium().value() == null) {
            throw missingFinancialData("现金价值要素缺失（产品/保费/生效日）, policyId=" + policy.getPolicyId());
        }
        int policyYear = PolicyCashValueQueryAppService.policyYearOf(
                effectiveDate.atStartOfDay(), valuationDate);
        PolicyCashValue cashValue = policyCashValuePort
                .getCashValue(tenantId, policy.getProductId(), policyYear,
                        policy.getTotalPremium().value(), effectiveDate, valuationDate)
                .orElseThrow(() -> missingFinancialData(
                        "现金价值不可得（产品域无适用退保价值策略）, policyId=" + policy.getPolicyId()));
        return cashValue.cashValue();
    }

    /**
     * 主险段（g04-05 减额缴清的核心取数对象）。
     * <p>
     * 🔴 取<b>主险段</b>而非全部险种段之和：减额缴清作用于主险合同的保额，附加险段不参与该不等式；
     * 保额口径与可执行字段 {@code policy.coverage.sumInsured}（{@code CoverageSumInsuredPolicyMaintenanceFieldExecutor}
     * 亦只认主险段）保持同源，避免同一判断在两处各写一份。
     * 险种段合同快照为空或无主险段即财务要素不全，失败关闭。
     * </p>
     * <p>
     * 🔴 <b>保额与其对象标识必须同一批取出</b>：{@code sumInsured} 与 {@code mainSegmentId} 是同一个
     * 主险段的两个分量，二者由本方法<b>一次解析</b>后同时下发——若下游分两次取数（一次取保额、一次取
     * 段标识），两次读到的是两个保单版本，减额缴清核定的「原保额」与随后回写的 {@code objectId} 可能
     * 指向不同的合同快照。
     * </p>
     */
    private PolicyProduct requireMainSegment(Policy policy) {
        List<PolicyProduct> products = policy.getPolicyProducts();
        if (products == null || products.isEmpty()) {
            throw missingFinancialData("原保额要素缺失（险种段合同快照为空）, policyId=" + policy.getPolicyId());
        }
        return products.stream()
                .filter(PolicyProduct::isMain)
                .findFirst()
                .orElseThrow(() -> missingFinancialData(
                        "原保额要素缺失（合同快照无主险段）, policyId=" + policy.getPolicyId()));
    }

    /** 主险段基本保额；未落库即财务要素不全（失败关闭）。 */
    private BigDecimal sumInsuredOf(PolicyProduct mainSegment) {
        Money sumInsured = mainSegment.sumInsured();
        if (sumInsured == null || sumInsured.value() == null) {
            throw missingFinancialData(
                    "原保额要素缺失（主险段保额未落库）, policyId=" + mainSegment.policyProductId());
        }
        return sumInsured.value();
    }

    /**
     * 主险段标识（{@code policyProductId}）：减额缴清回写 {@code policy.coverage.sumInsured} 时的
     * {@code objectId}，下游字段执行器逐字比对主险段 ID，故不得缺省。
     * <p>
     * {@code PolicyProduct} 对其标识无构造器校验，故此处显式失败关闭，不把空标识下发到跨域契约。
     * </p>
     */
    private String mainSegmentIdOf(PolicyProduct mainSegment) {
        String mainSegmentId = mainSegment.policyProductId();
        if (mainSegmentId == null || mainSegmentId.isBlank()) {
            throw missingFinancialData("主险段标识缺失（主险段未落库 policyProductId）");
        }
        return mainSegmentId;
    }

    /** 未偿本金 = 未结贷款未偿本金合计；无未结贷款为 0（业务事实，非兜底）。 */
    private BigDecimal resolveLoanOutstandingPrincipal(String tenantId, String policyId) {
        return outstandingLoans(tenantId, policyId).stream()
                .map(PolicyLoanAccount::outstandingPrincipal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 未偿利息 = Σ 未结贷款未偿利息（截至取数时点）。
     * <p>
     * 🔴 计息口径权威在贷款账户聚合（单利 365 分段，{@code PolicyLoanEntry.outstandingInterestAt}），
     * 本层只做汇总——<b>不得</b>再按「放款日 → 今日」自行计息：那会忽略部分还款已冲减的计息基数与
     * 已计提区间。冻结利率/放款日期缺失的失败关闭同由聚合守卫（{@code POLICY_FINANCIAL_DATA_MISSING}）。
     * </p>
     */
    private BigDecimal resolveLoanOutstandingInterest(String tenantId, String policyId) {
        return outstandingLoans(tenantId, policyId).stream()
                .map(PolicyLoanAccount::outstandingInterest)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 欠费 = 未结账单未缴金额合计；无欠费为 0（业务事实，非兜底）。 */
    private BigDecimal resolveArrearsAmount(String policyId) {
        return billingServicePort.getUnpaidBills(policyId)
                .stream()
                .map(UnpaidBill::unpaidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<PolicyLoanAccount> outstandingLoans(String tenantId, String policyId) {
        return policyLoanAccountRepository.findOutstanding(policyId, tenantId);
    }

    /** 生效日 = 保障起期；缺失即财务要素不全（失败关闭入口处统一拦截）。 */
    private LocalDate effectiveDateOf(Policy policy) {
        if (policy.getPolicyPeriod() == null || policy.getPolicyPeriod().insurancePeriodStart() == null) {
            throw missingFinancialData("保单生效日缺失(保障起期未落库), policyId=" + policy.getPolicyId());
        }
        return policy.getPolicyPeriod().insurancePeriodStart().toLocalDate();
    }

    /** 红利余额：无累计红利为 0（未参与分红是业务事实）。 */
    private BigDecimal dividendBalanceOf(Policy policy) {
        return policy.getAccumulatedDividend() != null && policy.getAccumulatedDividend().value() != null
                ? policy.getAccumulatedDividend().value()
                : BigDecimal.ZERO;
    }

    /** 投资账户价值：未挂接投资账户为 0（非投连/万能保单是业务事实）。 */
    private BigDecimal accountValueOf(Policy policy) {
        return policy.getInvestmentAccountValue() != null ? policy.getInvestmentAccountValue() : BigDecimal.ZERO;
    }

    /** 已缴期数 = 缴费流水中对账成功（RECONCILED）的笔数。 */
    private int paidInstallmentsOf(Policy policy) {
        if (policy.getPaymentRecords() == null) {
            return 0;
        }
        return (int) policy.getPaymentRecords().stream()
                .filter(record -> ReconciliationStatus.RECONCILED.equals(record.reconciliationStatus()))
                .count();
    }

    /**
     * 出单业务流水号：写侧聚合直取，<b>可空且不失败关闭</b>。
     * <p>
     * 该值仅作退保退费的 {@code originalBizNo} 溯源。存量保单的 {@code bizNo} 本就可空
     * （见聚合字段注释「可空以兼容存量保单」），而贷款与减额缴清路径根本不读它——若因它缺失而
     * 拒绝出快照，这两条与缴费条件无关的链路会被连坐。拒绝由退保使用点自行承担。
     * </p>
     */
    private String issuanceBizNoOf(Policy policy) {
        String bizNo = policy.getBizNo();
        return bizNo == null || bizNo.isBlank() ? null : bizNo;
    }

    /**
     * 年缴保费（未满期保费按日折算的基数）。
     * <p>
     * 口径 = Σ 各险种段 {@code 段保费 ÷ 段缴费年数}，除法由
     * {@link LinePaymentTerms#annualPremium(Money)} 独担——「缴费频次 → 每年期数」的换算表只在
     * 该值对象里有一份，本层不得另写一套。按段汇总而非「保单总保费 ÷ 主险缴费年数」：各段缴费条件
     * 可彼此独立（主险 20 年缴 + 附加险年年续是寿险常态），用单一缴费年数去除总保费会同时高估或
     * 低估各段。段保费取 {@link PolicyProduct#effectivePremium()}，与
     * {@link Policy#totalPremium()} 同一「拒保段不计入」口径。
     * </p>
     * <p>
     * 🔴 <b>失败关闭</b>：任一段「有保费却无缴费条件」即返回 null。宁可下游拒算，不可少算——按残缺
     * 段汇总出的年保费会直接缩小每一笔未满期退费。段列表为空、或全部段保费为空时同样返回 null
     * （「算不出」与「等于 0」必须可区分）。
     * </p>
     */
    private BigDecimal annualPremiumOf(Policy policy) {
        List<PolicyProduct> products = policy.getPolicyProducts();
        if (products == null || products.isEmpty()) {
            return null;
        }
        BigDecimal total = null;
        for (PolicyProduct line : products) {
            Money linePremium = line.effectivePremium();
            if (linePremium == null || linePremium.value() == null) {
                continue;
            }
            LinePaymentTerms paymentTerms = line.paymentTerms();
            if (paymentTerms == null) {
                return null;
            }
            Money annual = paymentTerms.annualPremium(linePremium);
            total = total == null ? annual.value() : total.add(annual.value());
        }
        return total;
    }

    /**
     * 保障止期：保单保障期间的迄期（终身型保单为 null）。
     * <p>
     * 未满期保费的剩余天数从退保日数到这里。取<b>保单保障止期</b>而非各段保障期间：末段可独立于保单
     * 主期间，但退保终止的是整张保单，口径须唯一。本层只做日历日截断（{@code toLocalDate()}），
     * 「截断到 [0, 365] 天」的口径属未满期保费公式，留在 product 域。
     * </p>
     */
    private LocalDate coverageEndDateOf(Policy policy) {
        if (policy.getPolicyPeriod() == null || policy.getPolicyPeriod().insurancePeriodEnd() == null) {
            return null;
        }
        return policy.getPolicyPeriod().insurancePeriodEnd().toLocalDate();
    }

    /**
     * 缴费是否期满（趸缴视为期满）。
     * <p>
     * 两种成立情形：① 缴费条件为趸缴（一次缴清，本无「未满期」可言）；② 已缴期数达到缴费总期数
     * （总期数口径由 {@link LinePaymentTerms#totalPeriods()} 独担）。无主险段或无缴费条件时返回
     * {@code false}——「判不出期满」不等于「已期满」，不得据此把未满期保费算成 0。
     * </p>
     * <p>
     * 🔴 不得以缴费流水状态（{@code PremiumPaymentStatus.COMPLETED}）作判据：该状态在生产链路
     * 无任何写入点（生产者缺席），用它判定会把所有保单误判为未缴清。
     * </p>
     */
    private boolean premiumPaidUpOf(Policy policy, int paidInstallments) {
        PolicyProduct mainSegment = policy.mainLine();
        if (mainSegment == null || mainSegment.paymentTerms() == null) {
            return false;
        }
        LinePaymentTerms paymentTerms = mainSegment.paymentTerms();
        return paymentTerms.isLumpSum() || paidInstallments >= paymentTerms.totalPeriods();
    }

    private DomainException missingFinancialData(String detail) {
        return new DomainException(PolicyErrorCode.POLICY_FINANCIAL_DATA_MISSING,
                "保单财务数据缺失: " + detail);
    }
}
