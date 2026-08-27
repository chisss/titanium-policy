package com.titanium.policy.application.orchestration.issuance.assembler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.titanium.metadata.enums.policy.CoverageAttachLevel;
import com.titanium.metadata.enums.policy.PolicyLineStatus;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.entity.insurance.InsuranceLine;
import com.titanium.policy.entity.policy.InsuredSubject;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.port.ClauseServicePort;
import com.titanium.policy.port.ProductServicePort;
import com.titanium.policy.valueobject.IssuanceRequest;
import com.titanium.policy.valueobject.policy.ClauseSnapshot;
import com.titanium.policy.valueobject.policy.CoverageSnapshot;
import com.titanium.policy.valueobject.pricing.PremiumCalculationReference;
import com.titanium.policy.valueobject.product.ProductBasicInfo;
import com.titanium.policy.valueobject.product.ProductClauseRef;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单险种段装配器（应用层，承保出单的取数 + 组装步骤）
 * <p>
 * 装配保单段 {@link PolicyProduct}（L2），核心工作是<b>冻结条款与责任快照</b>：向 product 域取
 * 产品绑定的条款（含版本），再向 clause 域取各条款下的责任清单，转为不可变快照挂到段上。
 * 保单一经签发即适用这些快照，条款域后续改版不影响存量保单。
 * </p>
 * <p>
 * 🔴 <b>责任挂载层级的决定在此</b>：clause 域只定义「有哪些责任」，不知道本次投保有几个标的。
 * 本装配器依责任类型与标的结构决定挂载：
 * </p>
 * <ul>
 *   <li>单标的段 → 责任挂该标的（{@code SUBJECT}），保额按标的计算（寿险身故金、车损险）；</li>
 *   <li>多标的段或无标的段 → 责任挂险种段（{@code LINE}），额度段内共享
 *       （医疗险年度累计保额、三者险赔第三方）。</li>
 * </ul>
 * <p>
 * <b>为何在 application</b>：装配需调 {@link ProductServicePort} 与 {@link ClauseServicePort}
 * 跨服务取数，依规约「调外部 Port 的编排属 application」。段的业务规则（构成不变量、保费守恒）
 * 仍在领域侧 {@code PolicyCompositionDomainService}。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyProductAssembler {

    private final ProductServicePort     productServicePort;
    private final ClauseServicePort      clauseServicePort;
    private final InsuranceLineAssembler insuranceLineAssembler;

    /**
     * 由出单请求装配保单险种段（一步出单用：无投保单中间态，直接由方案行装配）。
     *
     * @param request  出单请求
     * @param policyId 保单ID（段ID派生用）
     * @return 保单险种段列表
     */
    public List<PolicyProduct> assemble(IssuanceRequest request, String policyId) {
        List<InsuranceLine> insuranceLines = insuranceLineAssembler.assemble(request);
        return assembleFromInsuranceLines(insuranceLines, request.tenantId());
    }

    /**
     * 由投保段装配保单段（两步/三步出单用：承保时把投保段精化为承保段）。
     * <p>
     * 投保段已含保额/保费/期间/缴费/标的与核保结论，此处补齐条款与责任快照并映射段状态。
     * </p>
     *
     * @param insuranceLines 投保险种段列表
     * @param tenantId       租户ID
     * @return 保单险种段列表
     */
    public List<PolicyProduct> assembleFromInsuranceLines(List<InsuranceLine> insuranceLines, String tenantId) {
        return assembleFromInsuranceLines(insuranceLines, tenantId, List.of());
    }

    /**
     * 由投保段和确认计算引用装配保单段，按险种段ID冻结真实定价计划版本。
     */
    public List<PolicyProduct> assembleFromInsuranceLines(
            List<InsuranceLine> insuranceLines,
            String tenantId,
            List<PremiumCalculationReference> calculationReferences) {
        if (insuranceLines == null || insuranceLines.isEmpty()) {
            return List.of();
        }
        Map<String, String> policyProductIdByLineId = new HashMap<>();
        Map<String, PremiumCalculationReference> referenceByLineId = referencesByLineId(calculationReferences);
        boolean requireCalculationReference = calculationReferences != null && !calculationReferences.isEmpty();
        for (InsuranceLine insuranceLine : insuranceLines) {
            policyProductIdByLineId.put(insuranceLine.lineId(), UUID.randomUUID().toString());
        }

        List<PolicyProduct> lines = new ArrayList<>();
        for (InsuranceLine insuranceLine : insuranceLines) {
            String parentPolicyProductId = insuranceLine.parentLineId() != null
                    ? policyProductIdByLineId.get(insuranceLine.parentLineId()) : null;
            PremiumCalculationReference calculationReference = referenceByLineId.get(insuranceLine.lineId());
            if (requireCalculationReference && insuranceLine.countsTowardTotalPremium()
                    && !matches(insuranceLine, calculationReference)) {
                throw new IllegalArgumentException("险种段缺少匹配的确认计算版本引用: " + insuranceLine.lineId());
            }
            lines.add(assembleLine(insuranceLine, tenantId, policyProductIdByLineId.get(insuranceLine.lineId()),
                    parentPolicyProductId, calculationReference));
        }
        log.info("保单险种段装配完成: 段数={}, 责任数合计={}", lines.size(),
                lines.stream().mapToInt(line -> line.coverageSnapshots().size()).sum());
        return List.copyOf(lines);
    }

    /**
     * 装配单个保单段：取产品条款 → 取条款责任 → 决定挂载 → 组装段。
     */
    private PolicyProduct assembleLine(InsuranceLine insuranceLine, String tenantId, String policyProductId,
                                       String parentPolicyProductId,
                                       PremiumCalculationReference calculationReference) {
        List<ClauseSnapshot> clauseSnapshots = new ArrayList<>();
        List<CoverageSnapshot> coverageSnapshots = new ArrayList<>();
        ProductBasicInfo product = productServicePort.getProductBasicInfo(insuranceLine.productId(), tenantId);

        for (ProductClauseRef ref : productServicePort.getClauseRefs(insuranceLine.productId(),
                tenantId)) {
            ClauseSnapshot clauseSnapshot = clauseServicePort.fetchClauseSnapshot(ref.clauseId(), ref.mainClause(),
                    tenantId);
            if (clauseSnapshot != null) {
                clauseSnapshots.add(clauseSnapshot);
            }
            coverageSnapshots.addAll(attachCoverages(
                    clauseServicePort.fetchCoverageSnapshots(ref.clauseId(), tenantId), policyProductId,
                    insuranceLine.insuredSubjects()));
        }

        String productName = insuranceLine.productName();
        if ((productName == null || productName.isBlank()) && product != null) {
            productName = product.productName();
        }
        String productVersion = insuranceLine.productVersion() != null
                ? insuranceLine.productVersion() : product != null ? product.productVersion() : null;

        return new PolicyProduct(policyProductId, insuranceLine.lineNo(), insuranceLine.productCategory(),
                parentPolicyProductId, insuranceLine.productId(), insuranceLine.productCode(),
                productName, productVersion,
                calculationReference != null ? calculationReference.pricingPlanVersion() : null,
                insuranceLine.insuranceType(), insuranceLine.sumInsured(),
                insuranceLine.payablePremium(), insuranceLine.coveragePeriod(), insuranceLine.paymentTerms(),
                insuranceLine.underwritingConclusion(), resolveLineStatus(insuranceLine), List.copyOf(clauseSnapshots),
                insuranceLine.insuredSubjects() != null ? insuranceLine.insuredSubjects() : List.of(),
                List.copyOf(coverageSnapshots));
    }

    private Map<String, PremiumCalculationReference> referencesByLineId(
            List<PremiumCalculationReference> calculationReferences) {
        if (calculationReferences == null || calculationReferences.isEmpty()) {
            return Map.of();
        }
        Map<String, PremiumCalculationReference> references = new HashMap<>();
        for (PremiumCalculationReference reference : calculationReferences) {
            if (reference == null || reference.lineId() == null || reference.lineId().isBlank()) {
                continue;
            }
            PremiumCalculationReference previous = references.put(reference.lineId(), reference);
            if (previous != null) {
                throw new IllegalArgumentException("确认计算引用的险种段ID重复: " + reference.lineId());
            }
        }
        return Map.copyOf(references);
    }

    private boolean matches(InsuranceLine line, PremiumCalculationReference reference) {
        return reference != null && reference.pricingPlanVersion() != null
                && !reference.pricingPlanVersion().isBlank()
                && Objects.equals(line.productId(), reference.productId())
                && Objects.equals(line.productVersion(), reference.productVersion());
    }

    /**
     * 决定责任挂载层级并回填挂载对象ID。
     * <p>
     * 单标的段挂标的（保额按标的算）；多标的或无标的挂段（额度段内共享）。这一决定必须在此完成——
     * clause 域出具的责任快照挂载信息为空。
     * </p>
     */
    private List<CoverageSnapshot> attachCoverages(List<CoverageSnapshot> coverages, String policyProductId,
                                                   List<InsuredSubject> subjects) {
        if (coverages == null || coverages.isEmpty()) {
            return List.of();
        }
        boolean attachToSubject = subjects != null && subjects.size() == 1;
        String subjectId = attachToSubject ? subjects.get(0).subjectId() : null;
        List<CoverageSnapshot> attached = new ArrayList<>();
        for (CoverageSnapshot coverage : coverages) {
            attached.add(new CoverageSnapshot(coverage.coverageId(), coverage.coverageCode(), coverage.coverageName(),
                    coverage.coverageType(),
                    attachToSubject ? CoverageAttachLevel.SUBJECT : CoverageAttachLevel.LINE,
                    attachToSubject ? subjectId : policyProductId, coverage.coverageSumInsured(),
                    coverage.indemnityRatio(), coverage.deductibleType(), coverage.deductibleAmount(),
                    coverage.deductibleRatio(), coverage.waitingPeriodDays(), coverage.payoutRuleSummary()));
        }
        return attached;
    }

    /**
     * 投保段状态 → 保单段状态。
     * <p>
     * 承保时：核保通过（含条件承保）→ 已承保待生效；拒保 → 已拒保（保费不计入总保费）；
     * 未核保（一步出单免核保）→ 已承保待生效。
     * </p>
     */
    private PolicyLineStatus resolveLineStatus(InsuranceLine insuranceLine) {
        ConclusionType conclusion = insuranceLine.underwritingConclusion();
        if (conclusion == null) {
            return PolicyLineStatus.ACCEPTED;
        }
        return switch (conclusion) {
            case ACCEPT, MODIFY -> PolicyLineStatus.ACCEPTED;
            case REJECT -> PolicyLineStatus.REJECTED;
            case POSTPONE -> PolicyLineStatus.UNDERWRITING;
        };
    }

    /**
     * 险种段保费合计（拒保段不计入）。
     *
     * @param lines 保单险种段列表
     * @return 保费合计；无有效段时返回 null
     */
    public Money sumPremium(List<PolicyProduct> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        Money total = null;
        for (PolicyProduct line : lines) {
            Money linePremium = line.effectivePremium();
            if (linePremium == null) {
                continue;
            }
            total = total == null ? linePremium : total.add(linePremium);
        }
        return total;
    }

    /**
     * 汇总核保加费前的标准保费（拒保段不计入）。
     *
     * @param lines 已补齐试算保费的投保险种段
     * @return 标准保费合计；无有效段保费时返回 null
     */
    public Money sumStandardPremium(List<InsuranceLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        Money total = null;
        for (InsuranceLine line : lines) {
            if (!line.countsTowardTotalPremium() || line.premium() == null) {
                continue;
            }
            total = total == null ? line.premium() : total.add(line.premium());
        }
        return total;
    }
}
