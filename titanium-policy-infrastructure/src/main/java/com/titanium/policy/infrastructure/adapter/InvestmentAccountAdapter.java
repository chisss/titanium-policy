package com.titanium.policy.infrastructure.adapter;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.titanium.investment.api.InvestmentAccountApi;
import com.titanium.investment.api.InvestmentAccountApi.CreateAccountRequest;
import com.titanium.investment.query.result.InvestmentAccountQueryResult;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.port.InvestmentAccountPort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 投资账户服务适配器（driven adapter，位于 infrastructure）
 * <p>
 * {@link InvestmentAccountPort} 的基础设施实现，经投资域 {@link InvestmentAccountApi}（Feign）为投连/万能保单
 * 开立投资账户、查询账户价值，并把投资域返回防腐翻译为保单域 {@link Money}。开户/查询失败返回 null，
 * 由调用方（IssuanceSaga）兜底不阻断出单。与 {@code BillingServiceAdapter} 同构。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvestmentAccountAdapter implements InvestmentAccountPort {

    /** 默认初始单位净值 1.00（开户时净值，后续由投资域估值调整） */
    private static final BigDecimal DEFAULT_INITIAL_UNIT_PRICE = BigDecimal.ONE;
    /** 默认管理费率（年化） */
    private static final BigDecimal DEFAULT_MANAGEMENT_FEE_RATE = new BigDecimal("0.015");

    private final InvestmentAccountApi investmentAccountApi;

    @Override
    public String openAccount(String policyId, PolicyForm form, Money initialPremium, String tenantId) {
        String currency = initialPremium != null ? initialPremium.currency() : "CNY";
        CreateAccountRequest request = new CreateAccountRequest();
        request.setPolicyId(policyId);
        request.setAccountType(resolveAccountType(form));
        request.setInitialUnitPrice(DEFAULT_INITIAL_UNIT_PRICE);
        request.setCurrency(currency);
        request.setManagementFeeRate(DEFAULT_MANAGEMENT_FEE_RATE);

        ApiResponse<InvestmentAccountQueryResult> response = investmentAccountApi.createAccount(request);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            log.error("为保单开立投资账户失败: policyId={}, message={}", policyId,
                    response != null ? response.getMessage() : "无响应");
            return null;
        }
        return response.getData().getAccountId();
    }

    @Override
    public Money accountValue(String accountId, String tenantId) {
        ApiResponse<InvestmentAccountQueryResult> response = investmentAccountApi.getAccount(accountId);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            log.warn("查询投资账户价值失败: accountId={}", accountId);
            return null;
        }
        InvestmentAccountQueryResult data = response.getData();
        return Money.of(data.getAccountValue(), data.getCurrency());
    }

    /**
     * 保单形态 → 投资账户类型码：投连→UNIT_LINKED，万能→UNIVERSAL。
     */
    private String resolveAccountType(PolicyForm form) {
        if (form == PolicyForm.UNIVERSAL) {
            return "UNIVERSAL";
        }
        return "UNIT_LINKED";
    }
}
