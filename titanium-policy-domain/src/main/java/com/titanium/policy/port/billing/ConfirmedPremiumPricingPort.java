package com.titanium.policy.port;

import com.titanium.policy.valueobject.pricing.ConfirmedPremiumRequest;
import com.titanium.policy.valueobject.pricing.ConfirmedPremiumResult;

/**
 * Product 确认保费端口。
 * <p>
 * 出单应用层通过该端口提交固定产品版本、业务时点与核保调整，基础设施层负责调用 Product 的
 * {@code ISSUANCE_CONFIRM} 契约。返回结果是出单与计费唯一可使用的保费事实。
 * </p>
 */
public interface ConfirmedPremiumPricingPort {

    /**
     * 确认单个险种段的保费。
     *
     * @param request 确认计算请求
     * @return Product 已持久化的确认计算事实
     */
    ConfirmedPremiumResult confirm(ConfirmedPremiumRequest request);
}
