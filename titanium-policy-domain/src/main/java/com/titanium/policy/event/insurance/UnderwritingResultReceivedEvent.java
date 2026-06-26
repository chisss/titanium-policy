package com.titanium.policy.event.insurance;

import java.time.LocalDateTime;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;

/**
 * 核保结果接收事件
 */
public record UnderwritingResultReceivedEvent(
        String insuranceId,
        String underwritingId,
        ConclusionType resultCode,
        String opinion,
        String underwriterId,
        LocalDateTime underwritingTime,
        String underwritingCondition,
        String tenantId
) {}
