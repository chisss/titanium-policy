package com.titanium.policy.event;

import java.time.LocalDateTime;

import com.titanium.metadata.enums.policy.PolicyLineStatus;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.metadata.valueobject.Money;

/**
 * 险种段核保结论回写事件
 * <p>
 * 携带回写后的段状态与保单总保费——拒保段的保费不计入总保费，故段级核保结论会改变保单总保费，
 * 事件需同时携带重算后的总额供读侧同步。
 * </p>
 *
 * @param policyId          保单ID
 * @param policyProductId   险种段ID
 * @param conclusion        核保结论
 * @param lineStatus        回写后的段承保状态
 * @param underwritingId    核保单ID
 * @param opinion           核保意见
 * @param totalPremiumAfter 回写后的保单总保费（拒保段已剔除）
 * @param occurredAt        发生时间
 * @param operatorId        操作人ID
 * @param tenantId          租户ID
 */
public record LineUnderwritingResultUpdatedEvent(String policyId, String policyProductId, ConclusionType conclusion,
                                                 PolicyLineStatus lineStatus, String underwritingId, String opinion,
                                                 Money totalPremiumAfter, LocalDateTime occurredAt, String operatorId,
                                                 String tenantId) {
}
