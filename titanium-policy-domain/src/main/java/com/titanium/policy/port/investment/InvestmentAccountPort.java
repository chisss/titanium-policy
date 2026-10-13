package com.titanium.policy.port.investment;

import java.math.BigDecimal;

import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.valueobject.Money;

/**
 * 投资账户服务端口（出口/driven port）
 * <p>
 * 保单域表达对投资域的能力需求：为投连/万能保单开立投资账户、查询账户价值。具体的跨微服务调用（Feign）
 * 由基础设施层 {@code infrastructure.adapter} 的适配器实现，领域侧不依赖任何远程响应类型（防腐）。
 * </p>
 * <p>
 * Port 置于 {@code com.titanium.policy.port.investment}（与 aggregate 平级、按对端域归子包），符合六边形架构。
 * </p>
 */
public interface InvestmentAccountPort {

    /**
     * 为保单开立投资账户
     *
     * @param policyId 保单ID
     * @param form 保单形态（决定账户类型：投连/万能）
     * @param initialPremium 首期保费（作为账户初始价值参考）
     * @param managementFeeRate 管理费年费率（来自产品定价配置，G14/g14-04a；账户按此逐月计提管理费，
     *        由调用方经 {@code ProductServicePort.getManagementFeeRate} 取数后透传）。
     *        🔴 null = 产品未配置管理费率（合法形态，账户以「无费率」开户、不收费）——
     *        调用方不得在取数失败时用 null 顶替（failure 须上抛，见取数端口 javadoc）
     * @return 投资账户ID；开户失败返回 null（由调用方兜底，不阻断出单）
     */
    String openAccount(String policyId, PolicyForm form, Money initialPremium, BigDecimal managementFeeRate);

    /**
     * 查询投资账户价值
     *
     * @param accountId 投资账户ID
     * @return 账户价值；查询失败返回 null
     */
    Money accountValue(String accountId);
}
