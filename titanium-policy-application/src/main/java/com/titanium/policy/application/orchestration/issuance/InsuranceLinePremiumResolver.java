package com.titanium.policy.application.orchestration.issuance;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.titanium.common.exception.BusinessException;
import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.entity.insurance.InsuranceLine;
import com.titanium.policy.entity.policy.InsuredSubject;
import com.titanium.policy.port.PremiumCalculationGateway;
import com.titanium.policy.port.PremiumCalculationGateway.StandardPremiumRequest;
import com.titanium.policy.port.PremiumCalculationGateway.StandardPremiumResult;

import lombok.RequiredArgsConstructor;

/**
 * 投保险种段保费解析器。
 * <p>
 * 两步/三步出单的承保阶段必须先保证每个有效险种段都有保费，再把投保段精化为保单段。已有段保费
 * 属于上游已确认事实，不重复试算；仅对缺失保费的段调用计费端口。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class InsuranceLinePremiumResolver {

    private static final String DEFAULT_CURRENCY = CurrencyEnum.CNY.getCode();
    private static final String LINE_PREMIUM_CALCULATION_FAILED =
            "ISSUANCE_LINE_PREMIUM_CALCULATION_FAILED";

    private final PremiumCalculationGateway premiumCalculationGateway;

    /**
     * 补齐缺失的段级保费。
     *
     * @param insuranceLines 投保险种段
     * @param tenantId       租户ID
     * @param singleLineFallback 单险种整单计费结果兜底；多险种不允许把整单结果误写到某一段
     * @return 保费完整的不可变险种段列表
     */
    public List<InsuranceLine> resolve(List<InsuranceLine> insuranceLines, String tenantId,
                                       Money singleLineFallback) {
        if (insuranceLines == null || insuranceLines.isEmpty()) {
            return List.of();
        }

        List<InsuranceLine> resolved = new ArrayList<>();
        for (InsuranceLine line : insuranceLines) {
            if (line.premium() != null || !line.countsTowardTotalPremium()) {
                resolved.add(line);
                continue;
            }
            resolved.add(line.withPremium(resolveMissingPremium(line, tenantId, insuranceLines.size(),
                    singleLineFallback)));
        }
        return List.copyOf(resolved);
    }

    private Money resolveMissingPremium(InsuranceLine line, String tenantId, int lineCount,
                                        Money singleLineFallback) {
        if (lineCount == 1 && singleLineFallback != null && !singleLineFallback.isZero()) {
            return singleLineFallback;
        }
        try {
            StandardPremiumResult result = premiumCalculationGateway.calculatePremium(toRequest(line, tenantId));
            if (result == null || result.totalPremium() == null) {
                throw new IllegalStateException("计费域未返回段保费");
            }
            String currency = result.currency() != null ? result.currency() : resolveCurrency(line,
                    singleLineFallback);
            return Money.of(result.totalPremium(), currency);
        } catch (RuntimeException exception) {
            throw new BusinessException("险种段保费计算失败: lineNo=" + line.lineNo()
                    + ", productId=" + line.productId(), LINE_PREMIUM_CALCULATION_FAILED, exception);
        }
    }

    private StandardPremiumRequest toRequest(InsuranceLine line, String tenantId) {
        Map<String, Object> subjectData = new HashMap<>();
        InsuredSubject primaryInsured = line.primaryInsured();
        if (primaryInsured != null) {
            copySubjectAttribute(primaryInsured, subjectData, "age");
            copySubjectAttribute(primaryInsured, subjectData, "gender");
        }

        int totalPeriods = line.paymentTerms() != null ? line.paymentTerms().totalPeriods() : 1;
        String paymentMode = line.paymentTerms() != null && line.paymentTerms().paymentFrequency() != null
                ? line.paymentTerms().paymentFrequency().getCode() : null;
        int coverageYears = line.coveragePeriod() != null ? line.coveragePeriod().coverageYears() : 0;
        BigDecimal sumInsured = line.sumInsured() != null ? line.sumInsured().value() : null;
        return new StandardPremiumRequest(line.productId(), sumInsured, resolveCurrency(line, null), paymentMode,
                totalPeriods, coverageYears, Map.copyOf(subjectData), tenantId);
    }

    private void copySubjectAttribute(InsuredSubject subject, Map<String, Object> target, String key) {
        if (subject.attributes() != null && subject.attributes().get(key) != null) {
            target.put(key, subject.attributes().get(key));
        }
    }

    private String resolveCurrency(InsuranceLine line, Money fallback) {
        if (line.sumInsured() != null) {
            return line.sumInsured().currency();
        }
        return fallback != null ? fallback.currency() : DEFAULT_CURRENCY;
    }
}
