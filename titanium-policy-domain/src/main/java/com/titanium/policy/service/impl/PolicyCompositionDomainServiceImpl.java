package com.titanium.policy.service.impl;

import static com.titanium.metadata.errorcode.PolicyErrorCode.COMPOSITION_LINES_EMPTY;
import static com.titanium.metadata.errorcode.PolicyErrorCode.COMPOSITION_LINE_ID_DUPLICATE;
import static com.titanium.metadata.errorcode.PolicyErrorCode.COMPOSITION_LINE_ID_REQUIRED;
import static com.titanium.metadata.errorcode.PolicyErrorCode.COMPOSITION_MAIN_LINE_REQUIRED;
import static com.titanium.metadata.errorcode.PolicyErrorCode.COMPOSITION_MULTIPLE_MAIN_LINES;
import static com.titanium.metadata.errorcode.PolicyErrorCode.COMPOSITION_PREMIUM_NOT_CONSERVED;
import static com.titanium.metadata.errorcode.PolicyErrorCode.COMPOSITION_RIDER_PARENT_INVALID;
import static com.titanium.metadata.errorcode.PolicyErrorCode.COMPOSITION_RIDER_PARENT_REQUIRED;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.service.PolicyCompositionDomainService;
import com.titanium.policy.valueobject.RuleDecision;

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

    @Override
    public RuleDecision validate(List<PolicyProduct> lines, Money totalPremium) {
        if (lines == null || lines.isEmpty()) {
            return RuleDecision.rejected(COMPOSITION_LINES_EMPTY);
        }
        RuleDecision identity = validateIdentityUnique(lines);
        if (!identity.passed()) {
            return identity;
        }
        RuleDecision main = validateSingleMain(lines);
        if (!main.passed()) {
            return main;
        }
        RuleDecision rider = validateRiderParents(lines);
        if (!rider.passed()) {
            return rider;
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
     * 不变量②：有且仅有一个主险段。
     */
    private RuleDecision validateSingleMain(List<PolicyProduct> lines) {
        long mainCount = lines.stream().filter(PolicyProduct::isMain).count();
        if (mainCount == 0) {
            return RuleDecision.rejected(COMPOSITION_MAIN_LINE_REQUIRED);
        }
        return mainCount > 1
                ? RuleDecision.rejected(COMPOSITION_MULTIPLE_MAIN_LINES, mainCount)
                : RuleDecision.accepted();
    }

    /**
     * 不变量③：每个附加险段的 parent 指向该保单的主险段。
     */
    private RuleDecision validateRiderParents(List<PolicyProduct> lines) {
        String mainLineId = lines.stream()
                .filter(PolicyProduct::isMain)
                .map(PolicyProduct::policyProductId)
                .findFirst()
                .orElse(null);
        for (PolicyProduct line : lines) {
            if (!line.isRider()) {
                continue;
            }
            if (line.parentPolicyProductId() == null || line.parentPolicyProductId().isBlank()) {
                return RuleDecision.rejected(COMPOSITION_RIDER_PARENT_REQUIRED, line.lineNo());
            }
            if (!line.parentPolicyProductId().equals(mainLineId)) {
                return RuleDecision.rejected(COMPOSITION_RIDER_PARENT_INVALID, line.lineNo());
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
