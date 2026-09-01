package com.titanium.policy.infrastructure.adapter;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.titanium.common.exception.BusinessException;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.common.enums.PremiumCalculationPurpose;
import com.titanium.policy.port.ConfirmedPremiumPricingPort;
import com.titanium.policy.valueobject.pricing.ConfirmationContextSnapshot;
import com.titanium.policy.valueobject.pricing.ConfirmedPremiumRequest;
import com.titanium.policy.valueobject.pricing.ConfirmedPremiumResult;
import com.titanium.product.api.ProductPremiumCalculationApi;
import com.titanium.product.api.request.premium.PremiumCalculationRequest;
import com.titanium.product.api.request.config.UnderwritingAdjustmentRequest;
import com.titanium.product.api.response.premium.PremiumCalculationResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Policy 到 Product 确认保费契约的防腐适配器。
 * <p>
 * 在防腐边界把领域侧强类型 {@link ConfirmationContextSnapshot} 转为 Product 契约的
 * {@code Map<String, Object>} 快照（Product API 保持 Map 形态，本域不向其传导领域类型）。
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConfirmedPremiumPricingAdapter implements ConfirmedPremiumPricingPort {

    private final ProductPremiumCalculationApi productPremiumCalculationApi;

    @Override
    public ConfirmedPremiumResult confirm(ConfirmedPremiumRequest request) {
        ConfirmationContextSnapshot snapshot = request.requestSnapshot();
        PremiumCalculationRequest apiRequest = new PremiumCalculationRequest(
                request.calculationRequestId(), request.bizNo(), PremiumCalculationPurpose.ISSUANCE_CONFIRM.getCode(),
                request.productVersion(), request.businessTime(), request.currency(), request.sumInsured(),
                request.age(), request.gender(), request.paymentTermYears(), request.coverageTermYears(),
                request.paymentPeriods(), toSnapshotMap(snapshot), request.underwritingAdjustments().stream()
                        .map(adjustment -> new UnderwritingAdjustmentRequest(
                                adjustment.adjustmentCode(), adjustment.type(), adjustment.value(),
                                adjustment.reason(), adjustment.ruleVersion()))
                        .toList(), channelId(snapshot), policyYear(snapshot));
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
                    PolicyErrorCode.PRODUCT_PREMIUM_CONFIRMATION_FAILED);
        }
        PremiumCalculationResponse calculation = data;
        return new ConfirmedPremiumResult(
                calculation.calculationId(), calculation.status(), calculation.purpose(), calculation.productId(),
                calculation.productVersion(), calculation.currency(), calculation.standardPremium(),
                calculation.totalPremium(), calculation.pricingPlanVersion(), calculation.resultHash());
    }

    /**
     * 领域快照 → Product 契约 Map（防腐边界唯一转换点）。
     */
    private Map<String, Object> toSnapshotMap(ConfirmationContextSnapshot snapshot) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (snapshot == null) {
            return map;
        }
        map.put("lineId", snapshot.lineId());
        map.put("lineNo", snapshot.lineNo());
        map.put("productCode", snapshot.productCode());
        map.put("issuanceReference", snapshot.issuanceReference());
        if (snapshot.channelId() != null && !snapshot.channelId().isBlank()) {
            map.put("channelId", snapshot.channelId().trim());
        }
        map.put("policyYear", snapshot.policyYear());
        return map;
    }

    private String channelId(ConfirmationContextSnapshot snapshot) {
        if (snapshot == null || snapshot.channelId() == null || snapshot.channelId().isBlank()) {
            return null;
        }
        return snapshot.channelId().trim();
    }

    private int policyYear(ConfirmationContextSnapshot snapshot) {
        return snapshot == null ? 1 : Math.max(snapshot.policyYear(), 1);
    }
}
