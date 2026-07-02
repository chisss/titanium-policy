package com.titanium.policy.event;

import java.time.LocalDateTime;

import com.titanium.policy.common.enums.PolicyDataUpdateType;

/**
 * 保单数据变更事件（保全域触发：投保人/受益人/缴费方式/加减保）
 *
 * @deprecated 自阶段四 4C 起由 {@link PolicyEndorsedEvent} 取代（后者携带批单号/独立生效日/分类）。
 *             本事件从无命令产生（历史孤儿），保留仅为兼容潜在历史事件反序列化，新链路勿用。
 */
@Deprecated
public record PolicyDataUpdatedEvent(
        String policyId,
        PolicyDataUpdateType updateType,
        int newVersion,
        LocalDateTime updatedAt,
        String operatorId,
        String tenantId
) {}
