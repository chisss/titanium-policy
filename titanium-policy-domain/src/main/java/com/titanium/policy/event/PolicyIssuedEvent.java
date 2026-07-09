package com.titanium.policy.event;

import java.time.LocalDateTime;

import com.titanium.metadata.valueobject.Money;

/**
 * 保单签发事件
 * <p>
 * 携带承保关键要素（产品ID/保费/保额），供下游监管采集、自动分保等消费。险种类型不在本事件内，
 * 由下游按 {@code productId} 调产品域反查（policy 创建链无险种结构化来源）。
 * </p>
 */
public record PolicyIssuedEvent(
        String policyId,
        String policyNo,
        String productId,
        Money premium,
        Money sumInsured,
        LocalDateTime issueTime,
        String operatorId,
        String tenantId
) {}
