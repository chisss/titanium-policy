package com.titanium.policy.event.insurance;

import java.time.LocalDateTime;

/**
 * 核保结果接收事件
 */
public record UnderwritingResultReceivedEvent(
        String insuranceId,
        String underwritingId,
        String resultCode,
        String opinion,
        String underwriterId,
        LocalDateTime underwritingTime,
        String underwritingCondition,
        String tenantId
) {}
