package com.titanium.policy.event.insurance;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;

/**
 * 核保结果接收事件
 * <p>
 * {@code extraPremiumRatio} 为结构化加费率（UW-3）：次标准体修改条件承保时携带（如 0.30 表示加费30%），
 * 标准体/拒保时为 null。IssuanceSaga 监听此事件并存储加费率，在出单时并入保费。
 * </p>
 */
public record UnderwritingResultReceivedEvent(
        String insuranceId,
        String underwritingId,
        ConclusionType resultCode,
        String opinion,
        String underwriterId,
        LocalDateTime underwritingTime,
        String underwritingCondition,
        String tenantId,
        BigDecimal extraPremiumRatio
) {}
