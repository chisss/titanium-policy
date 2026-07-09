package com.titanium.policy.port;

import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.valueobject.Money;

/**
 * 投资账户服务端口（出口/driven port）
 * <p>
 * 保单域表达对投资域的能力需求：为投连/万能保单开立投资账户、查询账户价值。具体的跨微服务调用（Feign）
 * 由基础设施层 {@code infrastructure.adapter} 的适配器实现，领域侧不依赖任何远程响应类型（防腐）。
 * </p>
 * <p>
 * Port 置于 {@code com.titanium.policy.port}（与 aggregate 平级），符合六边形架构。
 * </p>
 */
public interface InvestmentAccountPort {

    /**
     * 为保单开立投资账户
     *
     * @param policyId 保单ID
     * @param form 保单形态（决定账户类型：投连/万能）
     * @param initialPremium 首期保费（作为账户初始价值参考）
     * @param tenantId 租户ID
     * @return 投资账户ID；开户失败返回 null（由调用方兜底，不阻断出单）
     */
    String openAccount(String policyId, PolicyForm form, Money initialPremium, String tenantId);

    /**
     * 查询投资账户价值
     *
     * @param accountId 投资账户ID
     * @param tenantId 租户ID
     * @return 账户价值；查询失败返回 null
     */
    Money accountValue(String accountId, String tenantId);
}
