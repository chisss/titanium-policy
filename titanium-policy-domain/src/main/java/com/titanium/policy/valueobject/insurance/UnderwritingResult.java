package com.titanium.policy.valueobject.insurance;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;

/**
 * 核保结果值对象
 * <p>
 * 存储核保域返回的结果，作为承保依据。核保结论统一使用 metadata 层
 * {@link ConclusionType}（接受/修改条件承保/拒绝/延期）强类型枚举。
 * {@code extraPremiumRatio} 为结构化加费率（UW-3）：次标准体修改条件承保时由核保域
 * 产出（如 0.30 表示在标准保费基础上加收30%），标准体/拒保时为 null。
 * 出单 Saga 在承保出单时据此调整应付保费（标准保费 × (1 + extraPremiumRatio)）。
 * </p>
 *
 * @param underwritingId      核保单号
 * @param resultCode          核保结论
 * @param underwritingOpinion 核保意见
 * @param underwriterId       核保人ID
 * @param underwritingTime    核保时间
 * @param condition           承保条件描述（如加费原因）
 * @param extraPremiumRatio   结构化加费率（比例加费时用；null 表示无加费）
 */
public record UnderwritingResult(String underwritingId, ConclusionType resultCode, String underwritingOpinion,
                                 String underwriterId, LocalDateTime underwritingTime, String condition,
                                 BigDecimal extraPremiumRatio) {
}
