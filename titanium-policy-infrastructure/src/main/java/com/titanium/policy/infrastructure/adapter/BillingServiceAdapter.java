package com.titanium.policy.infrastructure.adapter;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.titanium.billing.api.BillApi;
import com.titanium.billing.api.request.CreateBillRequest;
import com.titanium.billing.api.response.ApiResponse;
import com.titanium.billing.api.response.BillResponse;
import com.titanium.policy.port.BillingServicePort;
import com.titanium.policy.valueobject.billing.BillingResult;
import com.titanium.policy.valueobject.billing.PremiumBillRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 计费服务适配器（driven adapter，位于 infrastructure）
 * <p>
 * {@link BillingServicePort} 的基础设施实现，承保出单后经计费域 {@link BillApi}（Feign）开立首期保费账单，
 * 并把计费域返回防腐翻译为保单域值对象 {@link BillingResult}。与 {@code SyncUnderwritingDecisionAdapter} 同构。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingServiceAdapter implements BillingServicePort {

    /** 计费类型：保费 */
    private static final String BILLING_TYPE_PREMIUM = "PREMIUM";

    private final BillApi billApi;

    @Override
    public BillingResult createPremiumBill(PremiumBillRequest request) {
        log.info("承保出单后开立首期保费账单: policyId={}, tenantId={}", request.policyId(), request.tenantId());
        CreateBillRequest billRequest = new CreateBillRequest();
        billRequest.setPolicyId(request.policyId());
        billRequest.setCustomerId(request.customerId());
        billRequest.setBillingType(BILLING_TYPE_PREMIUM);
        billRequest.setAmount(request.premium() != null ? request.premium().value() : null);
        billRequest.setIssueDate(LocalDate.now());
        billRequest.setTenantId(request.tenantId());

        ApiResponse<BillResponse> response = billApi.createBill(billRequest);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            log.error("开立保费账单失败: policyId={}, message={}", request.policyId(),
                    response != null ? response.getMessage() : "无响应");
            return new BillingResult(false, null);
        }
        return new BillingResult(true, response.getData().getBillId());
    }
}
