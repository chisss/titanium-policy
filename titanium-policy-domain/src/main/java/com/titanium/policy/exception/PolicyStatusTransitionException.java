package com.titanium.policy.exception;

import com.titanium.metadata.exception.IllegalStateTransitionException;

/**
 * 保单状态流转非法异常
 * <p>
 * 当保单/投保单/投保意向单的状态机流转违反规则时抛出。适用于三个聚合根
 * （Policy/Insurance/Proposal）及其状态值对象的 transitionStatus 校验。
 * </p>
 *
 * @author wei.sun
 * @since 2026/6/23
 */
public class PolicyStatusTransitionException extends IllegalStateTransitionException {

    public PolicyStatusTransitionException(String aggregateType, String aggregateId,
                                           String fromStatus, String toStatus) {
        super(aggregateType, aggregateId, fromStatus, toStatus);
    }

    public PolicyStatusTransitionException(String aggregateType, String aggregateId,
                                           String fromStatus, String toStatus, String reason) {
        super(aggregateType, aggregateId, fromStatus, toStatus, reason);
    }
}
