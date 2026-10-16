package com.titanium.policy.service.impl;

import static com.titanium.metadata.errorcode.PolicyErrorCode.COMPOSITION_LINES_EMPTY;
import static com.titanium.metadata.errorcode.PolicyErrorCode.COMPOSITION_LINE_ID_DUPLICATE;
import static com.titanium.metadata.errorcode.PolicyErrorCode.COMPOSITION_LINE_ID_REQUIRED;
import static com.titanium.metadata.errorcode.PolicyErrorCode.COMPOSITION_MAIN_LINE_REQUIRED;
import static com.titanium.metadata.errorcode.PolicyErrorCode.COMPOSITION_MULTIPLE_MAIN_LINES;
import static com.titanium.metadata.errorcode.PolicyErrorCode.COMPOSITION_PREMIUM_NOT_CONSERVED;
import static com.titanium.metadata.errorcode.PolicyErrorCode.COMPOSITION_RIDER_PARENT_INVALID;
import static com.titanium.metadata.errorcode.PolicyErrorCode.COMPOSITION_RIDER_PARENT_REQUIRED;
import static com.titanium.metadata.errorcode.PolicyErrorCode.COMPOSITION_SUBJECT_SUM_INSURED_NOT_CONSERVED;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.titanium.metadata.valueobject.Money;
import com.titanium.metadata.valueobject.RuleDecision;
import com.titanium.policy.entity.policy.InsuredSubject;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.service.PolicyCompositionDomainService;

/**
 * 保单构成领域服务实现（纯领域逻辑，可脱离 Spring 用 {@code new} 直测）
 * <p>
 * 无任何 Port / 仓储 / 基础设施依赖，入参出参仅实体与值对象。标注 {@code @Service} 仅为
 * 便于容器注入，不影响其可独立实例化的纯净性。
 * </p>
 * <p>
 * 裁决结果复用统一的 {@link RuleDecision}（携带错误码 + 参数，文案由边界层按语言渲染），
 * 与投保要素校验共用同一载体——避免每类规则各造一个决策类（红线 18）。
 * </p>
 */
@Service
public class PolicyCompositionDomainServiceImpl implements PolicyCompositionDomainService {

    /** 保费守恒比对容差（元，容忍分摊舍入误差） */
    private static final BigDecimal PREMIUM_EPSILON = new BigDecimal("0.01");

    /** 分项保额守恒比对容差（元，与保费守恒同口径：容忍分项分摊的舍入误差） */
    private static final BigDecimal SUM_INSURED_EPSILON = new BigDecimal("0.01");

    @Override
    public RuleDecision validate(List<PolicyProduct> lines, Money totalPremium) {
        if (lines == null || lines.isEmpty()) {
            return RuleDecision.rejected(COMPOSITION_LINES_EMPTY);
        }
        RuleDecision identity = validateIdentityUnique(lines);
        if (!identity.passed()) {
            return identity;
        }
        RuleDecision main = validateMainLines(lines);
        if (!main.passed()) {
            return main;
        }
        RuleDecision rider = validateRiderParents(lines);
        if (!rider.passed()) {
            return rider;
        }
        RuleDecision subjectSumInsured = validateSubjectSumInsuredConservation(lines);
        if (!subjectSumInsured.passed()) {
            return subjectSumInsured;
        }
        return validatePremiumConservation(lines, totalPremium);
    }

    @Override
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
     * 不变量①：段ID与段序号在保单内唯一。
     */
    private RuleDecision validateIdentityUnique(List<PolicyProduct> lines) {
        Set<String> seenIds = new HashSet<>();
        Set<Integer> seenLineNos = new HashSet<>();
        for (PolicyProduct line : lines) {
            if (line.policyProductId() == null || line.policyProductId().isBlank()) {
                return RuleDecision.rejected(COMPOSITION_LINE_ID_REQUIRED);
            }
            if (!seenIds.add(line.policyProductId())) {
                return RuleDecision.rejected(COMPOSITION_LINE_ID_DUPLICATE, line.policyProductId());
            }
            if (!seenLineNos.add(line.lineNo())) {
                return RuleDecision.rejected(COMPOSITION_LINE_ID_DUPLICATE, line.lineNo());
            }
        }
        return RuleDecision.accepted();
    }

    /**
     * 不变量②：至少一个主险段（G11/g11-04 ①：由「有且仅有一个」放宽为「≥1」，支持交强+商业同单），
     * 且主险段间 <b>productId 唯一</b>（②：同一产品不得重复做主险段）。
     * <p>
     * 🔴 <b>唯一性判据必须落在 productId</b>（`G11/AC-05 反-1`）：交强与商业是<b>两个不同产品</b>、
     * 主险段各一，用 insuranceType（同为 AUTO）或险别做判据会把合法的双主险单误拒；
     * 而 productId 相同则是同一个产品被重复列为主险段——那才是「重复主险」的实义，与产品域
     * 「同一险种只能有一个主险段」的定性一致（错误码 {@code COMPOSITION_MULTIPLE_MAIN_LINES} 复用）。
     * </p>
     * <p>
     * 附加险依附关系（不变量③）在 {@link #validateRiderParents} 按主险段<b>集合</b>校验，
     * 不只认首个主险段。
     * </p>
     */
    private RuleDecision validateMainLines(List<PolicyProduct> lines) {
        long mainCount = lines.stream().filter(PolicyProduct::isMain).count();
        if (mainCount == 0) {
            return RuleDecision.rejected(COMPOSITION_MAIN_LINE_REQUIRED);
        }
        Set<String> seenMainProductIds = new HashSet<>();
        for (PolicyProduct line : lines) {
            if (!line.isMain()) {
                continue;
            }
            String productId = line.productId();
            if (productId == null || productId.isBlank()) {
                return RuleDecision.rejected(COMPOSITION_LINE_ID_REQUIRED);
            }
            if (!seenMainProductIds.add(productId)) {
                return RuleDecision.rejected(COMPOSITION_MULTIPLE_MAIN_LINES, productId);
            }
        }
        return RuleDecision.accepted();
    }

    /**
     * 不变量③：每个附加险段的 parent 指向该保单的<b>某一</b>主险段。
     * <p>
     * 🔴 G11/g11-04 ③：多主险段（交强+商业同单）时附加险可挂在<b>任一</b>主险段上——此前取
     * {@code findFirst()} 只认第一个主险段，挂第二个主险段的附加险会被误拒（AC-05 反-2）。
     * 校验范围与「合法主险段集合」一致，不引入任何「取首段」语义。
     * </p>
     */
    private RuleDecision validateRiderParents(List<PolicyProduct> lines) {
        Set<String> mainLineIds = new HashSet<>();
        for (PolicyProduct line : lines) {
            if (line.isMain()) {
                mainLineIds.add(line.policyProductId());
            }
        }
        for (PolicyProduct line : lines) {
            if (!line.isRider()) {
                continue;
            }
            if (line.parentPolicyProductId() == null || line.parentPolicyProductId().isBlank()) {
                return RuleDecision.rejected(COMPOSITION_RIDER_PARENT_REQUIRED, line.lineNo());
            }
            if (!mainLineIds.contains(line.parentPolicyProductId())) {
                return RuleDecision.rejected(COMPOSITION_RIDER_PARENT_INVALID, line.lineNo());
            }
        }
        return RuleDecision.accepted();
    }

    /**
     * 不变量⑤：多标的段的分项保额守恒——Σ 标的保额 == 段保额（G11/g11-05，容差 1 分）。
     * <p>
     * 段保额是「总盘子」，各标的保额是分项（车险多车、企财多分项）；与保费守恒（不变量④）同族，
     * 都是「声明值 vs 明细合计」的守恒断言。
     * </p>
     * <p>
     * <b>四条口径</b>：
     * </p>
     * <ol>
     *   <li><b>范围</b>：仅多标的段（{@code size > 1}）校验——单标的段（含无标的段）维持
     *       「标的保额可空 = 取段保额」的现有语义<b>不变</b>（向后兼容，不校验，防误伤存量）；</li>
     *   <li><b>null 口径</b>：标的保额为 null 时<b>按段保额代入</b>——与 {@code InsuranceLineAssembler.toSubject}
     *       的装配口径逐字一致（防「校验端与装配端口径漂移」）。由此多标的段中任一标的未声明保额
     *       必然不守恒被拒，即「多标的段须显式声明各标的保额」；</li>
     *   <li><b>容差</b>：1 分（{@link #SUM_INSURED_EPSILON}），容忍分项分摊舍入误差；</li>
     *   <li><b>段保额缺失</b>：段保额本身为 null 时跳过——该场景由出单要素校验
     *       （{@code ELIGIBILITY_SUM_INSURED_REQUIRED}）负责，不在本不变量职责范围。</li>
     * </ol>
     * <p>
     * <b>兜底路径判定（G11/g11-05 第③项）</b>：{@code InsuranceLineAssembler.subjectsFromInsuredParties}
     * 的「每被保险人复制段保额」行为仅对<b>单标的段合法</b>（与「可空=取段保额」同效）；多标的下
     * Σ = N×段保额 ≠ 段保额，被本不变量判为<b>非法</b>（拒绝出单）——多标的段必须经
     * {@code IssuancePlanLine.SubjectIntent.subjectSumInsured} 显式声明各标的保额。
     * </p>
     */
    private RuleDecision validateSubjectSumInsuredConservation(List<PolicyProduct> lines) {
        for (PolicyProduct line : lines) {
            List<InsuredSubject> subjects = line.insuredSubjects();
            if (subjects == null || subjects.size() <= 1) {
                continue;
            }
            Money lineSumInsured = line.sumInsured();
            if (lineSumInsured == null) {
                continue;
            }
            BigDecimal summed = BigDecimal.ZERO;
            for (InsuredSubject subject : subjects) {
                summed = summed.add(subject.subjectSumInsured() != null
                        ? subject.subjectSumInsured().value()
                        : lineSumInsured.value());
            }
            BigDecimal diff = summed.subtract(lineSumInsured.value()).abs();
            if (diff.compareTo(SUM_INSURED_EPSILON) > 0) {
                return RuleDecision.rejectedAtLine(COMPOSITION_SUBJECT_SUM_INSURED_NOT_CONSERVED,
                        line.lineNo(), lineSumInsured.value(), summed);
            }
        }
        return RuleDecision.accepted();
    }

    /**
     * 不变量④：保单总保费 = Σ 计入段的保费（拒保段不计入，容差 1 分）。
     * <p>
     * 调用方未声明总保费时跳过该校验（出单期总保费由本服务汇总产生，无需自校验）。
     * </p>
     */
    private RuleDecision validatePremiumConservation(List<PolicyProduct> lines, Money totalPremium) {
        if (totalPremium == null) {
            return RuleDecision.accepted();
        }
        Money summed = sumPremium(lines);
        BigDecimal summedValue = summed != null ? summed.value() : BigDecimal.ZERO;
        BigDecimal diff = totalPremium.value().subtract(summedValue).abs();
        return diff.compareTo(PREMIUM_EPSILON) > 0
                ? RuleDecision.rejected(COMPOSITION_PREMIUM_NOT_CONSERVED, totalPremium.value(), summedValue)
                : RuleDecision.accepted();
    }
}
