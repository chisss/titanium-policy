package com.titanium.policy.entity.policy;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.CoverageAttachLevel;
import com.titanium.metadata.enums.policy.PolicyLineStatus;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.valueobject.policy.ClauseSnapshot;
import com.titanium.policy.valueobject.policy.CoverageSnapshot;
import com.titanium.policy.valueobject.policy.LineCoveragePeriod;
import com.titanium.policy.valueobject.policy.LinePaymentTerms;

/**
 * 险种段实体（L2）—— 一个产品在本保单内的承保单元
 * <p>
 * <b>这是「一张保单包含多个险种」的建模基础。</b>一张保单（{@code Policy}）含 1..N 个险种段：
 * 寿险的「终身寿主险 + 附加重疾 + 附加医疗」、车险的「交强险 + 车损险 + 三者险」，
 * 每段对应一个产品，有各自的保额、保费、保障期间、缴费条件、核保结论与承保状态。
 * </p>
 * <p>
 * 🔴 <b>四个「可独立」是本实体存在的理由</b>——缺了它们，以下场景无法表达：
 * </p>
 * <ol>
 *   <li>{@link #underwritingConclusion} 段级核保结论 → 「主险承保通过、某附加险被拒保」</li>
 *   <li>{@link #paymentTerms} 段级缴费条件 → 「主险 20 年缴、附加医疗 1 年期年年续」</li>
 *   <li>{@link #coveragePeriod} 段级保障期间 → 「主险终身、附加重疾至 70 岁」</li>
 *   <li>{@link #lineStatus} 段级承保状态 → 「附加险单独退保而主险持续有效」</li>
 * </ol>
 * <p>
 * 主险与附加险的关系以 {@link #productCategory} 与 {@link #parentPolicyProductId} 表达：
 * 一张保单有且仅有一个 MAIN 段，RIDER 段的 parent 必须指向该主险段。单险种保单即段列表长度为 1。
 * </p>
 * <p>
 * 取代原 {@code entity/InsuranceProduct} 与 {@code entity/insurance/InsuranceProduct}
 * （双份同名且仅承载编码+保额，无段级独立要素）。
 * </p>
 *
 * @param policyProductId        险种段ID（保单内唯一）
 * @param lineNo                 段序号（对应出单请求的 planLine 序号，便于回溯）
 * @param productCategory        产品类别（MAIN 主险 / RIDER 附加险）
 * @param parentPolicyProductId  依附的主险段ID（RIDER 必填，MAIN 为 null）
 * @param productId              产品ID（指向 product 域）
 * @param productCode            产品编码（快照）
 * @param productName            产品名称（快照）
 * @param productVersion         产品版本（快照，锁定出单时点的产品定义）
 * @param insuranceType          险种三级分类
 * @param sumInsured             本险种保额（独立）
 * @param premium                本险种保费（独立；拒保段不计入保单总保费）
 * @param coveragePeriod         本险种保障期间（可独立于保单主期间）
 * @param paymentTerms           本险种缴费条件（可独立于其他段）
 * @param underwritingConclusion 本险种核保结论（可独立；未核保为 null）
 * @param lineStatus             本险种承保状态
 * @param clauseSnapshots        本险种绑定的条款快照（L2.5）
 * @param insuredSubjects        本险种承保的标的（L3）
 * @param coverageSnapshots      本险种的保险责任快照（L4，含挂载层级）
 */
public record PolicyProduct(String policyProductId, int lineNo, ProductCategory productCategory,
                            String parentPolicyProductId, String productId, String productCode, String productName,
                            String productVersion, InsuranceProductType insuranceType, Money sumInsured, Money premium,
                            LineCoveragePeriod coveragePeriod, LinePaymentTerms paymentTerms,
                            ConclusionType underwritingConclusion, PolicyLineStatus lineStatus,
                            List<ClauseSnapshot> clauseSnapshots, List<InsuredSubject> insuredSubjects,
                            List<CoverageSnapshot> coverageSnapshots) {

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
     * 本段保费是否计入保单总保费。
     * <p>
     * 拒保段不计入（保费守恒不变量按此口径校验）。
     * </p>
     *
     * @return 计入返回 {@code true}
     */
    public boolean countsTowardTotalPremium() {
        return lineStatus == null || lineStatus.countsTowardTotalPremium();
    }

    /**
     * 本段是否处于可理赔的保障状态。
     *
     * @return 保障中返回 {@code true}
     */
    @JsonIgnore
    public boolean isCovering() {
        return lineStatus != null && lineStatus.isCovering();
    }

    /**
     * 计入总保费的保费金额（拒保段返回零）。
     *
     * @return 有效保费；无保费或拒保时返回 null
     */
    public Money effectivePremium() {
        return countsTowardTotalPremium() ? premium : null;
    }

    /**
     * 回写本段核保结论与对应承保状态。
     * <p>
     * 结论到状态的映射内聚于此：通过/条件承保 → 已承保待生效；拒绝 → 已拒保；暂缓 → 仍核保中。
     * </p>
     *
     * @param conclusion 核保结论
     * @return 回写后的新实例
     */
    public PolicyProduct withUnderwritingConclusion(ConclusionType conclusion) {
        PolicyLineStatus newStatus = conclusion == null ? lineStatus : switch (conclusion) {
            case ACCEPT, MODIFY -> PolicyLineStatus.ACCEPTED;
            case REJECT -> PolicyLineStatus.REJECTED;
            case POSTPONE -> PolicyLineStatus.UNDERWRITING;
        };
        return withConclusionAndStatus(conclusion, newStatus);
    }

    /**
     * 变更本段承保状态（生效 / 退保 / 满期等流转）。
     *
     * @param newStatus 目标状态
     * @return 变更后的新实例
     */
    public PolicyProduct withLineStatus(PolicyLineStatus newStatus) {
        return withConclusionAndStatus(underwritingConclusion, newStatus);
    }

    /**
     * 本段责任保额合计（用于与段保额比对，或展示保障总额）。
     *
     * @return 责任保额合计；无责任时返回 null
     */
    public Money totalCoverageSumInsured() {
        if (coverageSnapshots == null || coverageSnapshots.isEmpty()) {
            return null;
        }
        Money total = null;
        for (CoverageSnapshot coverage : coverageSnapshots) {
            if (coverage.coverageSumInsured() == null) {
                continue;
            }
            total = total == null ? coverage.coverageSumInsured() : total.add(coverage.coverageSumInsured());
        }
        return total;
    }

    /**
     * 取挂在本险种段上的责任（赔付对象为第三方或额度段内共享，如三者险、医疗险年度累计额度）。
     *
     * @return 段级责任列表
     */
    public List<CoverageSnapshot> lineLevelCoverages() {
        return coveragesAt(CoverageAttachLevel.LINE, policyProductId);
    }

    /**
     * 取挂在指定标的上的责任（赔付对象为标的自身，如车损险、身故金）。
     *
     * @param subjectId 标的ID
     * @return 该标的的责任列表
     */
    public List<CoverageSnapshot> coveragesOfSubject(String subjectId) {
        return coveragesAt(CoverageAttachLevel.SUBJECT, subjectId);
    }

    /**
     * 按ID查找本段标的。
     *
     * @param subjectId 标的ID
     * @return 匹配的标的；未找到返回 null
     */
    public InsuredSubject subjectOf(String subjectId) {
        if (insuredSubjects == null || subjectId == null) {
            return null;
        }
        return insuredSubjects.stream()
                .filter(subject -> subjectId.equals(subject.subjectId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 本段主条款（一段仅一个主条款）。
     *
     * @return 主条款快照；未配置返回 null
     */
    public ClauseSnapshot mainClause() {
        if (clauseSnapshots == null) {
            return null;
        }
        return clauseSnapshots.stream()
                .filter(ClauseSnapshot::isMainClause)
                .findFirst()
                .orElse(null);
    }

    /**
     * 按挂载层级与挂载对象筛选责任。
     */
    private List<CoverageSnapshot> coveragesAt(CoverageAttachLevel level, String refId) {
        if (coverageSnapshots == null) {
            return List.of();
        }
        List<CoverageSnapshot> matched = new ArrayList<>();
        for (CoverageSnapshot coverage : coverageSnapshots) {
            if (coverage.isAttachedTo(level, refId)) {
                matched.add(coverage);
            }
        }
        return List.copyOf(matched);
    }

    /**
     * 以新的核保结论与状态复制本段（record 不可变，变更返回副本）。
     */
    private PolicyProduct withConclusionAndStatus(ConclusionType conclusion, PolicyLineStatus status) {
        return new PolicyProduct(policyProductId, lineNo, productCategory, parentPolicyProductId, productId,
                productCode, productName, productVersion, insuranceType, sumInsured, premium, coveragePeriod,
                paymentTerms, conclusion, status, clauseSnapshots, insuredSubjects, coverageSnapshots);
    }
}
