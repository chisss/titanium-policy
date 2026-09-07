package com.titanium.policy.event;

import java.time.LocalDateTime;

import com.titanium.metadata.valueobject.Money;

/**
 * 保单激活事件
 * <p>
 * {@code totalPremium} 承载激活时点的应收保费事实（共享内核 {@link Money}），
 * 供 billing 等下游域消费激活事件自动开立保费账单（dev-607 计费自动触发链），
 * 免去下游跨域查询保费；历史事件（无保费字段）反序列化时该字段为 null。
 * </p>
 */
public record PolicyActivatedEvent(String policyId, String insuranceId, String bizNo, LocalDateTime activatedAt,
                                   String tenantId, Money totalPremium) {

    /**
     * 兼容历史事件构造与回放（无保费字段）。
     */
    public PolicyActivatedEvent(String policyId, String insuranceId, String bizNo, LocalDateTime activatedAt,
                                String tenantId) {
        this(policyId, insuranceId, bizNo, activatedAt, tenantId, null);
    }

    /**
     * 兼容历史事件构造与回放。
     */
    public PolicyActivatedEvent(String policyId, LocalDateTime activatedAt, String tenantId) {
        this(policyId, null, null, activatedAt, tenantId, null);
    }
}
