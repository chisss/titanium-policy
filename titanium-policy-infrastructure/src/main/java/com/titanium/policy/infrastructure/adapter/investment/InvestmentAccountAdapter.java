package com.titanium.policy.infrastructure.adapter.investment;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.titanium.investment.api.InvestmentAccountApi;
import com.titanium.investment.api.InvestmentAccountApi.CreateAccountRequest;
import com.titanium.investment.query.result.InvestmentAccountQueryResult;
import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.port.investment.InvestmentAccountPort;

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
    /**
     * 默认管理费率（年化）。
     * <p>
     * 🔴 <b>登记（G14/g14-04，2026-10-12）：本常量是「费率无权威来源」的临时兜底，待产品参数化后移除</b>——
     * 管理费率应由产品/合同参数给定，而非代码常量。阻断依据（实测三条）：
     * ① 产品侧 {@code PricingBasicRule.typeSpecificConfig}（柔性 {@code Map}，注释即「投连险投资账户费率」）
     * 的结构化改造（{@code →InvestmentConfig}）未实施（见 {@code docs/领域模型系统性整改路线图.md}）；
     * ② 产品费用组件（{@code t_product_charge_component}）是目录定义（无费率值列），且无投连/万能产品的
     * 管理费组件数据；③ 缺口文档已定性「费率本身没有权威来源」（{@code Titanium业务功能缺口与后续推进指导文档-2026-09.md:636}）。
     * </p>
     * <p>
     * 🔴 在权威来源落地前<b>不得删除本兜底</b>：开户费率为空即「未配置」⇒ 投资域计提按 AC-06 静默跳过
     * ⇒ 管理费永久收不到（功能倒退）。替代路径落地时须同批：产品侧参数 + 本 Adapter 取数 + 本常量删除。
     * </p>
     */
    private static final BigDecimal DEFAULT_MANAGEMENT_FEE_RATE = new BigDecimal("0.015");

    private final InvestmentAccountApi investmentAccountApi;

    @Override
    public String openAccount(String policyId, PolicyForm form, Money initialPremium) {
        String currency = initialPremium != null ? initialPremium.currency() : CurrencyEnum.CNY.getCode();
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
    public Money accountValue(String accountId) {
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
