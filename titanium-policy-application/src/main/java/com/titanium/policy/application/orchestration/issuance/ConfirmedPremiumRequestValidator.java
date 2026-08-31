package com.titanium.policy.application.orchestration.issuance;

import java.util.List;

import org.springframework.stereotype.Component;

import com.titanium.common.exception.BusinessException;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.common.enums.PremiumCalculationPurpose;
import com.titanium.policy.common.enums.PremiumCalculationStatus;
import com.titanium.policy.entity.insurance.InsuranceLine;
import com.titanium.policy.valueobject.pricing.ConfirmedPremiumRequest;
import com.titanium.policy.valueobject.pricing.ConfirmedPremiumResult;
import com.titanium.policy.valueobject.pricing.PremiumCalculationReference;

/**
 * 确认保费请求校验器。
 * <p>
 * 把原先散落在 {@code InsuranceLinePremiumConfirmationService#toRequest} 中的 6 处 if 校验
 * 收敛为独立校验器（规约红线 19：&gt;3 参数 if 校验链须独立成类），校验顺序与原实现保持一致，
 * 异常消息与错误码枚举携带（红线 16）。
 * </p>
 */
@Component
public class ConfirmedPremiumRequestValidator {

    /**
     * 校验可确认保费的险种段列表非空。
     */
    public void validateLines(List<InsuranceLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException("出单缺少可确认保费的险种段",
                    PolicyErrorCode.ISSUANCE_PREMIUM_LINE_LIST_EMPTY);
        }
    }

    /**
     * 校验确认保费请求的段级入参（产品版本、保额、业务时点、标的信息、保障期限）。
     * <p>
     * 校验顺序与原 toRequest 一致：行信息 → 标的信息 → 保障期限。
     * </p>
     */
    public void validate(ConfirmedPremiumRequest request) {
        validateLineInfo(request);
        validateSubjectInfo(request);
        validateCoveragePeriod(request);
    }

    /**
     * 校验至少存在一个可计费的险种段（确认结果汇总后）。
     */
    public void validateBillableExists(Money total, List<PremiumCalculationReference> references) {
        if (total == null || references.isEmpty()) {
            throw new BusinessException("出单没有可计费的承保险种段",
                    PolicyErrorCode.ISSUANCE_PREMIUM_BILLABLE_LINE_MISSING);
        }
    }

    /**
     * 校验 Product 返回的确认保费事实完整且与请求版本匹配。
     */
    public void validateResult(InsuranceLine line, ConfirmedPremiumResult result) {
        if (result == null || !PremiumCalculationStatus.CONFIRMED.getCode().equals(result.status())
                || !PremiumCalculationPurpose.ISSUANCE_CONFIRM.getCode().equals(result.purpose())
                || result.calculationId() == null || result.resultHash() == null || result.standardPremium() == null
                || result.totalPremium() == null || result.pricingPlanVersion() == null
                || result.pricingPlanVersion().isBlank() || !line.productId().equals(result.productId())
                || !line.productVersion().equals(result.productVersion())) {
            throw new BusinessException("Product 返回的确认保费事实不完整或版本不匹配: lineNo=" + line.lineNo(),
                    PolicyErrorCode.ISSUANCE_PREMIUM_RESULT_MISMATCH);
        }
    }

    private void validateLineInfo(ConfirmedPremiumRequest request) {
        if (request.productId() == null || request.productVersion() == null || request.productVersion().isBlank()
                || request.sumInsured() == null || request.businessTime() == null) {
            throw new BusinessException("险种段缺少产品版本、保额或业务时点: lineNo=" + lineNoOf(request),
                    PolicyErrorCode.ISSUANCE_PREMIUM_LINE_INFO_MISSING);
        }
    }

    private void validateSubjectInfo(ConfirmedPremiumRequest request) {
        if (request.age() == null || request.gender() == null || request.gender().isBlank()) {
            throw new BusinessException("险种段缺少被保险人年龄或性别: lineNo=" + lineNoOf(request),
                    PolicyErrorCode.ISSUANCE_PREMIUM_SUBJECT_INFO_MISSING);
        }
    }

    private void validateCoveragePeriod(ConfirmedPremiumRequest request) {
        if (request.coverageTermYears() == null || request.coverageTermYears() < 1) {
            throw new BusinessException("险种段保障期限必须大于零: lineNo=" + lineNoOf(request),
                    PolicyErrorCode.ISSUANCE_PREMIUM_COVERAGE_PERIOD_INVALID);
        }
    }

    private Integer lineNoOf(ConfirmedPremiumRequest request) {
        return request.requestSnapshot() != null ? request.requestSnapshot().lineNo() : null;
    }
}
