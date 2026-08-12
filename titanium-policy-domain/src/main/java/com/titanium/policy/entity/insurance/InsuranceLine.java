package com.titanium.policy.entity.insurance;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyLineStatus;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.entity.policy.InsuredSubject;
import com.titanium.policy.valueobject.policy.LineCoveragePeriod;
import com.titanium.policy.valueobject.policy.LinePaymentTerms;

/**
 * 投保险种段实体（投保阶段的 L2）
 * <p>
 * 投保单阶段的险种段。与保单段 {@code PolicyProduct} 是<b>渐进精化</b>关系而非同一类型：
 * </p>
 * <table border="1">
 *   <caption>三阶段段结构对比</caption>
 *   <tr><th>要素</th><th>ProposalLine 意向</th><th>InsuranceLine 投保（本类）</th><th>PolicyProduct 承保</th></tr>
 *   <tr><td>保额保费</td><td>意向值</td><td><b>试算值</b></td><td>承保值（含加费）</td></tr>
 *   <tr><td>核保结论</td><td>不存在</td><td><b>段级结论</b></td><td>结果快照</td></tr>
 *   <tr><td>条款责任快照</td><td>无</td><td>无（核保后才锁版本）</td><td>有（签发即冻结）</td></tr>
 *   <tr><td>标的</td><td>简要</td><td><b>完整（核保需全属性）</b></td><td>完整快照</td></tr>
 * </table>
 * <p>
 * 🔴 <b>段级核保结论是本类存在的核心理由</b>：改造前投保单以 {@code InsuranceBasicInfo.productCodeList}
 * （{@code List<String>} 裸编码）承载多险种，核保结论却只有投保单级单一值
 * （{@code Insurance.underwritingResult}），导致「主险承保通过、某附加险被拒保」这一寿险常态
 * <b>无法表达</b>。段化后每段独立持有结论与状态。
 * </p>
 *
 * @param lineId                 段ID（投保单内唯一）
 * @param lineNo                 段序号（对应出单请求的 planLine 序号）
 * @param productCategory        产品类别（MAIN 主险 / RIDER 附加险）
 * @param parentLineId           依附的主险段ID（RIDER 必填，MAIN 为 null）
 * @param productId              产品ID
 * @param productCode            产品编码
 * @param productName            产品名称
 * @param insuranceType          险种三级分类
 * @param sumInsured             本险种投保保额
 * @param premium                本险种试算保费
 * @param coveragePeriod         本险种保障期间
 * @param paymentTerms           本险种缴费条件
 * @param insuredSubjects        本险种承保标的（完整属性，供核保取风险要素）
 * @param underwritingConclusion 本险种核保结论（未核保为 null）
 * @param extraPremiumRatio      本险种核保加费率（无加费为 null）
 * @param lineStatus             本险种投保段状态
 */
public record InsuranceLine(String lineId, int lineNo, ProductCategory productCategory, String parentLineId,
                            String productId, String productCode, String productName,
                            InsuranceProductType insuranceType, Money sumInsured, Money premium,
                            LineCoveragePeriod coveragePeriod, LinePaymentTerms paymentTerms,
                            List<InsuredSubject> insuredSubjects, ConclusionType underwritingConclusion,
                            BigDecimal extraPremiumRatio, PolicyLineStatus lineStatus) {

    /**
     * 是否为主险段。
     *
     * @return 主险返回 {@code true}
     */
    @JsonIgnore
    public boolean isMain() {
        return productCategory == ProductCategory.MAIN;
    }

    /**
     * 是否为附加险段。
     *
     * @return 附加险返回 {@code true}
     */
    @JsonIgnore
    public boolean isRider() {
        return productCategory == ProductCategory.RIDER;
    }

    /**
     * 本段是否已核保通过（含条件承保）。
     *
     * @return 核保通过返回 {@code true}
     */
    @JsonIgnore
    public boolean isUnderwritingApproved() {
        return underwritingConclusion == ConclusionType.ACCEPT || underwritingConclusion == ConclusionType.MODIFY;
    }

    /**
     * 本段是否被拒保。
     *
     * @return 拒保返回 {@code true}
     */
    @JsonIgnore
    public boolean isRejected() {
        return underwritingConclusion == ConclusionType.REJECT || lineStatus == PolicyLineStatus.REJECTED;
    }

    /**
     * 本段保费是否计入投保单总保费（拒保段不计入）。
     *
     * @return 计入返回 {@code true}
     */
    public boolean countsTowardTotalPremium() {
        return !isRejected();
    }

    /**
     * 回写本段核保结论与加费率，并同步段状态。
     *
     * @param conclusion        核保结论
     * @param extraPremiumRatio 加费率（无加费传 null）
     * @return 回写后的新实例
     */
    public InsuranceLine withUnderwritingResult(ConclusionType conclusion, BigDecimal extraPremiumRatio) {
        PolicyLineStatus newStatus = conclusion == null ? lineStatus : switch (conclusion) {
            case ACCEPT, MODIFY -> PolicyLineStatus.ACCEPTED;
            case REJECT -> PolicyLineStatus.REJECTED;
            case POSTPONE -> PolicyLineStatus.UNDERWRITING;
        };
        return new InsuranceLine(lineId, lineNo, productCategory, parentLineId, productId, productCode, productName,
                insuranceType, sumInsured, premium, coveragePeriod, paymentTerms, insuredSubjects, conclusion,
                extraPremiumRatio, newStatus);
    }

    /**
     * 加费后应付保费（保费 ×(1 + 加费率)）。
     * <p>
     * 核保加费按段计算——不同险种的加费率可不同（如附加重疾因既往症加费 30%、主险不加费）。
     * 拒保段返回 null（不计入总保费）。
     * </p>
     *
     * @return 加费后保费；拒保或无保费时返回 null
     */
    public Money payablePremium() {
        if (isRejected() || premium == null) {
            return null;
        }
        if (extraPremiumRatio == null || extraPremiumRatio.signum() <= 0) {
            return premium;
        }
        return premium.multiply(BigDecimal.ONE.add(extraPremiumRatio));
    }

    /**
     * 首要被保险人（本段标的列表首个人身类标的）。
     * <p>
     * 供核保取风险要素（年龄/性别/职业）使用。
     * </p>
     *
     * @return 首要被保险人标的；无人身类标的时返回 null
     */
    public InsuredSubject primaryInsured() {
        if (insuredSubjects == null || insuredSubjects.isEmpty()) {
            return null;
        }
        return insuredSubjects.stream().filter(InsuredSubject::isPerson).findFirst().orElse(null);
    }
}
