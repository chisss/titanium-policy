package com.titanium.policy.api.response.policy;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 保单财务快照查询结果（保单域对外契约，g04-02）。
 * <p>
 * 保全域退费/贷款/减额缴清等现金价值类动作的取数底座：现金价值、贷款未偿本息、欠费、
 * 红利与账户价值、生效日与已缴期数。所有字段由保单域<b>写侧强一致</b>组装——
 * 保单要素经事件流重建聚合取得，现金价值转调产品域退保价值策略（口径复用
 * {@code CashValueQueryAppService}），欠费转调计费域账单，消费方不得自行推导任何口径。
 * </p>
 * <p>
 * 🔴 失败关闭契约：保单不存在或任一财务要素缺失时本响应<b>不产生</b>，调用方收到业务码失败
 * （{@code POLICY_NOT_EXIST} 或 {@code POLICY_FINANCIAL_DATA_MISSING}），禁止以 0 兜底。
 * </p>
 *
 * @param policyId 保单ID
 * @param cashValue 现金价值金额
 * @param sumInsured 主险段基本保额（g04-05 减额缴清核定「新保额 ≤ 原保额」的原保额基准）
 * @param mainSegmentId 主险段ID（{@code policyProductId}）；减额缴清回写
 *                      {@code policy.coverage.sumInsured} 时该字段的 {@code objectId}，与
 *                      {@code sumInsured} 同为「主险段」的两个分量，由保单域同批下发
 * @param loanOutstandingPrincipal 贷款未偿本金（无未结贷款为 0）
 * @param loanOutstandingInterest 贷款未偿利息（无未结贷款为 0）
 * @param arrearsAmount 欠费金额（未缴保费合计，无欠费为 0）
 * @param dividendBalance 红利账户余额
 * @param accountValue 投资账户价值
 * @param effectiveDate 保单生效日
 * @param paidInstallments 已缴期数（缴费对账成功的流水数，口径由保单域内聚）
 * @param issuanceBizNo 出单业务流水号（退保退费的 {@code originalBizNo} 溯源；<b>可空</b>，
 *                      存量保单无此值，贷款/减额缴清路径不读它）
 * @param annualPremium 年缴保费（未满期保费按日折算的基数；<b>可空</b>，见下方口径说明）
 * @param coverageEndDate 保障止期（未满期保费剩余天数的终点；<b>可空</b>，终身型保单无终止日）
 * @param premiumPaidUp 缴费是否期满（趸缴视为期满）
 * <p>
 * 🔴 <b>退费口径四项（{@code issuanceBizNo}/{@code annualPremium}/{@code coverageEndDate}/
 * {@code premiumPaidUp}）是可空项，不参与失败关闭</b>——它们只服务退保退费的未满期保费基准，
 * 贷款与减额缴清路径不读，故不因缺失而拒绝出响应；「缺失」的拒绝责任由使用点承担。
 * 唯一保留的约束是「非空即不得为负」。
 * </p>
 * <p>
 * 🔴 {@code annualPremium} 由保单域按 Σ(段保费 ÷ 段缴费年数) 算好下发，
 * 消费方<b>不得</b>按缴费频次自行还原：频次→每年期数的换算表只在保单域
 * {@code LinePaymentTerms} 里有一份，另写一套即产生第二套保费口径。
 * </p>
 */
public record PolicyFinancialResponse(
        String policyId,
        BigDecimal cashValue,
        BigDecimal sumInsured,
        String mainSegmentId,
        BigDecimal loanOutstandingPrincipal,
        BigDecimal loanOutstandingInterest,
        BigDecimal arrearsAmount,
        BigDecimal dividendBalance,
        BigDecimal accountValue,
        LocalDate effectiveDate,
        Integer paidInstallments,
        String issuanceBizNo,
        BigDecimal annualPremium,
        LocalDate coverageEndDate,
        boolean premiumPaidUp) {

    public PolicyFinancialResponse {
        // 金额字段不得为负：反序列化即失败关闭，坏数据不出域、不静默转 0
        requireNonNegative(cashValue, "cashValue");
        requireNonNegative(sumInsured, "sumInsured");
        requireNonNegative(loanOutstandingPrincipal, "loanOutstandingPrincipal");
        requireNonNegative(loanOutstandingInterest, "loanOutstandingInterest");
        requireNonNegative(arrearsAmount, "arrearsAmount");
        requireNonNegative(dividendBalance, "dividendBalance");
        requireNonNegative(accountValue, "accountValue");
        if (paidInstallments == null || paidInstallments < 0) {
            throw new IllegalArgumentException("已缴期数不得为空或为负");
        }
        // 主险段标识是下游回写保额时的 objectId（字段执行器逐字比对），缺失即不该出域
        if (mainSegmentId == null || mainSegmentId.isBlank()) {
            throw new IllegalArgumentException("主险段标识不得为空");
        }
        // 退费口径项允许缺省（见 javadoc），但给了值就不能是负数——负年保费会把未满期保费算成负额退款
        requireNullableNonNegative(annualPremium, "annualPremium");
    }

    private static void requireNonNegative(BigDecimal amount, String field) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("财务金额字段 " + field + " 不得为空或为负");
        }
    }

    private static void requireNullableNonNegative(BigDecimal amount, String field) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("财务金额字段 " + field + " 不得为负");
        }
    }
}
