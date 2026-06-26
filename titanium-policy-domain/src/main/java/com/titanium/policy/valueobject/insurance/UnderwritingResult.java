package com.titanium.policy.valueobject.insurance;

import java.time.LocalDateTime;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;

/**
 * 核保结果值对象
 * <p>
 * 存储核保域返回的结果，作为承保依据。核保结论统一使用 metadata 层
 * {@link ConclusionType}（接受/修改条件承保/拒绝/延期）强类型枚举。
 * </p>
 *
 * @param underwritingId 核保单号
 * @param resultCode 核保结论
 * @param underwritingOpinion 核保意见
 * @param underwriterId 核保人ID
 * @param underwritingTime 核保时间
 * @param condition 承保条件，如加费
 */
public record UnderwritingResult(String underwritingId, ConclusionType resultCode, String underwritingOpinion,
                                 String underwriterId, LocalDateTime underwritingTime, String condition) {
}
