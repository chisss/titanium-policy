package com.titanium.policy.valueobject.financial;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 保单财务快照（域侧值对象，g04-02 取数契约的领域视图）。
 * <p>
 * 保全域退费/贷款/减额缴清等现金价值类动作的取数底座。由
 * {@code PolicyFinancialSnapshotQueryAppService} 从<b>写侧强一致</b>数据组装：
 * 保单要素经事件流重建聚合取得（不读最终一致的读模型），现金价值转调产品域退保价值策略
 * （口径复用现金价值查询服务），欠费转调计费域账单，贷款未偿本息取本地状态存储
 * {@code t_policy_loan}。
 * </p>
 * <p>
 * 失败关闭：任一财务要素缺失（生效日/产品/保费/贷款利率）或外部域取数失败时本值对象
 * <b>不产生</b>，由调用链抛出携带错误码的领域异常；「无记录 = 0」（无未结贷款、无欠费、
 * 无红利、无投资账户）是业务事实，与「取不到」严格区分。
 * </p>
 *
 * @param cashValue 现金价值金额（产品域退保价值策略计算）
 * @param sumInsured 主险段基本保额（减额缴清核定「新保额 ≤ 原保额」的原保额基准，g04-05）
 * @param mainSegmentId 主险段ID（{@code policyProductId}；减额缴清回写 {@code policy.coverage.sumInsured}
 *                       时该字段的 {@code objectId}，与 {@code sumInsured} 同为「主险段」的两个分量，
 *                       故与它同源取出、同批下发——分两次取数会取到两个保单版本，见 g04-05）
 * @param loanOutstandingPrincipal 贷款未偿本金合计（无未结贷款为 0）
 * @param loanOutstandingInterest 贷款未偿利息合计（无未结贷款为 0）
 * @param arrearsAmount 欠费金额（未结账单未缴金额合计，无欠费为 0）
 * @param dividendBalance 红利账户余额（无累计红利为 0）
 * @param accountValue 投资账户价值（未挂接投资账户为 0）
 * @param effectiveDate 保单生效日（保障起期）
 * @param paidInstallments 已缴期数（缴费对账成功的流水数，口径由保单域内聚）
 * @param issuanceBizNo 出单业务流水号（退保退费登记的 {@code originalBizNo}；取自写侧聚合
 *                      {@code Policy#getBizNo()}，<b>可空</b>——存量保单无此值，且贷款/减额缴清
 *                      路径不读它，故缺它<b>不</b>失败关闭，由退保使用点自行拒绝）
 * @param annualPremium 年缴保费（未满期保费按日折算的基数；<b>可空</b>，口径见下方说明）
 * @param coverageEndDate 保障止期（未满期保费剩余天数的终点；<b>可空</b>，终身型保单无终止日）
 * @param premiumPaidUp 缴费是否期满（趸缴视为期满）
 * <p>
 * 🔴 <b>后四项仅供「退保退费口径」消费，属可空项、不参与失败关闭</b>：贷款与减额缴清路径不读它们，
 * 若因其缺失而拒绝出快照，会让这两条与缴费条件无关的链路被连坐。缺失的语义分两种，由使用点区分——
 * {@code annualPremium}/{@code coverageEndDate} 缺失表示「保单域算不出年保费」（如险种段未落缴费条件），
 * 退保在<b>未满期保费基准</b>下须拒绝而非按 0 退；{@code issuanceBizNo} 缺失表示存量保单无出单流水号。
 * </p>
 * <p>
 * 🔴 <b>年保费口径的唯一性</b>：{@code annualPremium} = Σ 各险种段 {@code 段总保费 ÷ 段缴费年数}
 * （除法由 {@link com.titanium.policy.valueobject.policy.LinePaymentTerms#annualPremium} 独担）。
 * 按段汇总而非「保单总保费 ÷ 主险缴费年数」，是因为各段缴费条件可彼此独立（主险 20 年缴 + 附加险
 * 年缴年年续是寿险常态），用单一缴费年数去除总保费会同时高估或低估各段。
 * </p>
 */
public record PolicyFinancialSnapshot(
        BigDecimal cashValue,
        BigDecimal sumInsured,
        String mainSegmentId,
        BigDecimal loanOutstandingPrincipal,
        BigDecimal loanOutstandingInterest,
        BigDecimal arrearsAmount,
        BigDecimal dividendBalance,
        BigDecimal accountValue,
        LocalDate effectiveDate,
        int paidInstallments,
        String issuanceBizNo,
        BigDecimal annualPremium,
        LocalDate coverageEndDate,
        boolean premiumPaidUp) {
}
