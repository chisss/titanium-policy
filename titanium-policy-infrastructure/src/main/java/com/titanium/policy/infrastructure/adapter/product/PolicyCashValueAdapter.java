package com.titanium.policy.infrastructure.adapter.product;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.port.product.PolicyCashValuePort;
import com.titanium.policy.valueobject.product.PolicyCashValue;
import com.titanium.product.api.ProductSurrenderValueApi;
import com.titanium.product.api.response.premium.CashValueQueryResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单现金价值适配器。
 * <p>
 * {@link PolicyCashValuePort} 的基础设施实现，调用产品域 {@link ProductSurrenderValueApi} 的
 * 只读现金价值端点，并把产品域 Response 翻译为保单域防腐 record。
 * </p>
 * <p>
 * 🔴 <b>降级语义</b>：产品域未配置该产品 + 保单年度的已发布退保价值策略（业务码失败），或远程调用
 * 不可用，一律返回空 Optional，由调用方按业务保守处理——涉金场景「取不到即不垫缴」比「报错中断
 * 整批扫描」更安全，且失败不掩盖成功处理的其余保单。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolicyCashValueAdapter implements PolicyCashValuePort {

    private final ProductSurrenderValueApi productSurrenderValueApi;

    @Override
    public Optional<PolicyCashValue> getCashValue(String tenantId, String productId, int policyYear,
            BigDecimal customerPayable, LocalDate policyEffectiveDate, LocalDate valuationDate) {
        ApiResponse<CashValueQueryResponse> response;
        try {
            // businessTime 传 null：由产品域按当前时间定位适用策略版本
            response = productSurrenderValueApi.queryCashValue(productId, policyYear, customerPayable,
                    policyEffectiveDate, valuationDate, null, tenantId);
        } catch (RuntimeException exception) {
            log.error("查询产品现金价值失败: tenantId={}, productId={}, policyYear={}", tenantId, productId, policyYear,
                    exception);
            return Optional.empty();
        }
        if (response == null || !response.isSuccess() || response.getData() == null) {
            log.info("产品域无适用退保价值策略: tenantId={}, productId={}, policyYear={}, code={}", tenantId, productId,
                    policyYear, response != null ? response.getCode() : null);
            return Optional.empty();
        }
        CashValueQueryResponse data = response.getData();
        return Optional.of(new PolicyCashValue(data.productId(), policyYear, data.withinCoolingOff(),
                data.refundType(), data.cashValueRate(), data.cashValue(), data.policyCode(), data.policyVersion(),
                data.policyContentHash()));
    }
}
