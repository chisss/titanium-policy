package com.titanium.policy.infrastructure.adapter;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.titanium.billing.api.PremiumCalculationApi;
import com.titanium.billing.api.PremiumCalculationApi.PremiumCalculationRequest;
import com.titanium.billing.api.PremiumCalculationApi.PremiumCalculationResponse;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.port.PremiumCalculationGateway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保费计算网关适配器（driven adapter，位于 infrastructure）
 * <p>
 * 实现 {@link PremiumCalculationGateway}，经 Feign {@link PremiumCalculationApi} 调 billing 域
 * 计算标准保费，防腐翻译为保单域值对象 {@link StandardPremiumResult}。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PremiumCalculationAdapter implements PremiumCalculationGateway {

    private final PremiumCalculationApi premiumCalculationApi;

    @Override
    public StandardPremiumResult calculatePremium(StandardPremiumRequest request) {
        log.info("调 billing 计算标准保费: productId={}, sumInsured={}, tenantId={}",
                request.productId(), request.sumInsured(), request.tenantId());

        Map<String, Object> subjectData = request.subjectData() != null
                ? new HashMap<>(request.subjectData()) : new HashMap<>();

        PremiumCalculationRequest apiRequest = new PremiumCalculationRequest(
                request.productId(),
                request.sumInsured(),
                request.currency(),
                request.paymentMode(),
                request.totalPeriods(),
                request.coverageYears(),
                null,           // firstDueDate：纯计算不需要
                subjectData,
                "issuance-saga" // operatedBy
        );

        ApiResponse<PremiumCalculationResponse> response =
                premiumCalculationApi.calculatePremium(apiRequest, request.tenantId());

        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new RuntimeException("billing 保费计算失败: productId=" + request.productId()
                    + ", msg=" + (response != null ? response.getMessage() : "无响应"));
        }

        PremiumCalculationResponse data = response.getData();
        log.info("billing 保费计算成功: productId={}, 总保费={}", request.productId(), data.totalPremium());

        return new StandardPremiumResult(
                data.totalPremium(),
                data.installmentAmount(),
                data.periods(),
                data.currency()
        );
    }
}
