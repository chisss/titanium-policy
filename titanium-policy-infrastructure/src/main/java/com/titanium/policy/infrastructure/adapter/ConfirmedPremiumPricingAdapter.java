package com.titanium.policy.infrastructure.adapter;

import org.springframework.stereotype.Component;

import com.titanium.common.exception.BusinessException;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.port.ConfirmedPremiumPricingPort;
import com.titanium.policy.valueobject.pricing.ConfirmedPremiumRequest;
import com.titanium.policy.valueobject.pricing.ConfirmedPremiumResult;
import com.titanium.product.api.ProductPremiumCalculationApi;
import com.titanium.product.api.request.PremiumCalculationRequest;
import com.titanium.product.api.request.UnderwritingAdjustmentRequest;
import com.titanium.product.api.response.PremiumCalculationResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Policy 到 Product 确认保费契约的防腐适配器。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConfirmedPremiumPricingAdapter implements ConfirmedPremiumPricingPort {

    private static final String ISSUANCE_CONFIRM = "ISSUANCE_CONFIRM";

    private final ProductPremiumCalculationApi productPremiumCalculationApi;

    @Override
    public ConfirmedPremiumResult confirm(ConfirmedPremiumRequest request) {
        PremiumCalculationRequest apiRequest = new PremiumCalculationRequest(
                request.calculationRequestId(), request.bizNo(), ISSUANCE_CONFIRM, request.productVersion(),
                request.businessTime(), request.currency(), request.sumInsured(), request.age(), request.gender(),
                request.paymentTermYears(), request.coverageTermYears(), request.paymentPeriods(),
                request.requestSnapshot(), request.underwritingAdjustments().stream()
                        .map(adjustment -> new UnderwritingAdjustmentRequest(
                                adjustment.adjustmentCode(), adjustment.type(), adjustment.value(),
                                adjustment.reason(), adjustment.ruleVersion()))
                        .toList(), channelId(request), policyYear(request));
        ApiResponse<PremiumCalculationResponse> response = productPremiumCalculationApi.confirm(
                request.productId(), apiRequest, request.tenantId());
        PremiumCalculationResponse data = response != null ? response.getData() : null;
        log.info("Product 确认保费响应: productId={}, bizNo={}, success={}, status={}, purpose={}, productVersion={}, pricingPlanVersion={}, resultHash={}",
                request.productId(), request.bizNo(), response != null && response.isSuccess(),
                data != null ? data.status() : null, data != null ? data.purpose() : null,
                data != null ? data.productVersion() : null, data != null ? data.pricingPlanVersion() : null,
                data != null ? data.resultHash() : null);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new BusinessException(
                    "Product 确认保费失败: productId=" + request.productId() + ", message="
                            + (response == null ? "无响应" : response.getMessage()),
                    "PRODUCT_PREMIUM_CONFIRMATION_FAILED");
        }
        PremiumCalculationResponse calculation = data;
        return new ConfirmedPremiumResult(
                calculation.calculationId(), calculation.status(), calculation.purpose(), calculation.productId(),
                calculation.productVersion(), calculation.currency(), calculation.standardPremium(),
                calculation.totalPremium(), calculation.pricingPlanVersion(), calculation.resultHash());
    }

    private String channelId(ConfirmedPremiumRequest request) {
        Object value = request.requestSnapshot().get("channelId");
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value).trim();
    }

    private int policyYear(ConfirmedPremiumRequest request) {
        Object value = request.requestSnapshot().get("policyYear");
        if (value instanceof Number number) {
            return Math.max(number.intValue(), 1);
        }
        if (value instanceof String text) {
            try {
                return Math.max(Integer.parseInt(text), 1);
            } catch (NumberFormatException ignored) {
                return 1;
            }
        }
        return 1;
    }
}
