package com.titanium.policy.application.orchestration.issuance.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.titanium.policy.entity.insurance.InsuranceLine;
import com.titanium.policy.port.product.ProductServicePort;
import com.titanium.policy.valueobject.product.ProductSumInsuredTiers;

import lombok.RequiredArgsConstructor;

/**
 * 险种段产品的责任级保额档次配置取数器（G02/AC-09）
 * <p>
 * 把「险种段列表 → 各产品的档次配置载体」这一取数动作收敛到一处：两条建单链路
 * （两步出单的 {@code IssuanceOrchestrator} 与三步出单的 {@code ProposalIssuanceSaga}）
 * 都必须把配置作为<b>命令分量</b>交给投保单聚合——聚合不得依赖 Port，段级保额校验所需的
 * 外部配置只能由编排层取回后传入。取数规则若两处各写一份，必然漂移。
 * </p>
 * <p>
 * 🔴 <b>不兜底</b>：远程失败由 Adapter 抛 {@code RemoteDependencyUnavailableException}，
 * 此处不 try/catch。档次配置是段级保额校验的必要输入，取不到就不应继续建单——兜底成空清单会把
 * 「服务不可用」静默变成「该产品没配档次」而绕过校验（对端契约：未配置返回空列表，非 404）。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class ProductSumInsuredTiersLoader {

    private final ProductServicePort productServicePort;

    /**
     * 取各险种段产品的档次配置（按 {@code productId} 去重，每产品一次远程调用）。
     *
     * @param lines 已装配的险种段列表
     * @return 各产品的档次配置载体；段上产品ID均缺失时为空列表
     */
    public List<ProductSumInsuredTiers> load(List<InsuranceLine> lines) {
        return lines.stream()
                .map(InsuranceLine::productId)
                .filter(Objects::nonNull)
                .distinct()
                .map(productServicePort::getCoverageSumInsuredConfigs)
                .toList();
    }
}
