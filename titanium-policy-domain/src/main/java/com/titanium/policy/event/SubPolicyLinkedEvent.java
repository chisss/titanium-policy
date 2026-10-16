package com.titanium.policy.event;

import java.time.LocalDateTime;

import org.axonframework.serialization.Revision;

/**
 * 子保单已挂载事件（父保单登记一个子保单后发布）
 */
@Revision("1")
public record SubPolicyLinkedEvent(String parentPolicyId, String childPolicyId, String groupId, int subPolicyCount,
                                   LocalDateTime linkedAt, String operatorId, String tenantId) {
}
