package com.titanium.policy.event;

import java.time.LocalDateTime;

import org.axonframework.serialization.Revision;

import com.titanium.metadata.valueobject.Money;

/**
 * 保单激活事件
 * <p>
 * {@code totalPremium} 承载激活时点的应收保费事实（共享内核 {@link Money}），
 * 供 billing 等下游域消费激活事件自动开立保费账单（dev-607 计费自动触发链），
 * 免去下游跨域查询保费；历史事件（无保费字段）反序列化时该字段为 null。
 * </p>
 * <p>
 * 🔴 <b>m27-04/PKG-04 新增计费要素</b>：{@code productId} 与 {@code policyStartDate} 随事件外发，
 * 使 billing 域可在本地投影「保单计费要素快照」并<b>直接</b>向 product 域查询现金价值
 * （现金价值规则本就是 product 的权威），从而删除 billing→policy 的同步依赖、打破服务间依赖环。
 * 两者对历史事件均为 null（字段引入前的事件），消费方须按「要素不全 ⇒ 取不到现金价值」降级，
 * 不得以缺省值代填。
 * </p>
 */
@Revision("1")
public record PolicyActivatedEvent(String policyId, String insuranceId, String bizNo, LocalDateTime activatedAt,
                                   String tenantId, Money totalPremium, String productId,
                                   LocalDateTime policyStartDate) {

    /**
     * 兼容历史事件构造与回放（无计费要素字段）。
     */
    public PolicyActivatedEvent(String policyId, String insuranceId, String bizNo, LocalDateTime activatedAt,
                                String tenantId, Money totalPremium) {
        this(policyId, insuranceId, bizNo, activatedAt, tenantId, totalPremium, null, null);
    }

    /**
     * 兼容历史事件构造与回放（无保费字段）。
     */
    public PolicyActivatedEvent(String policyId, String insuranceId, String bizNo, LocalDateTime activatedAt,
                                String tenantId) {
        this(policyId, insuranceId, bizNo, activatedAt, tenantId, null, null, null);
    }

    /**
     * 兼容历史事件构造与回放。
     */
    public PolicyActivatedEvent(String policyId, LocalDateTime activatedAt, String tenantId) {
        this(policyId, null, null, activatedAt, tenantId, null, null, null);
    }
}
