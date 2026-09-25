package com.titanium.policy.exception;

import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.exception.IllegalStateTransitionException;

/**
 * 保单状态流转非法异常
 * <p>
 * 当保单/投保单/投保意向单的状态机流转违反规则时抛出。适用于三个聚合根
 * （Policy/Insurance/Proposal）及其状态值对象的 transitionStatus 校验。
 * </p>
 * <p>
 * 错误码固定为 {@link PolicyErrorCode#POLICY_STATUS_TRANSITION_ILLEGAL}（HTTP 409）。此前本类转发父类的
 * 裸串构造器，错误码退化为符号串 {@code ILLEGAL_STATE_TRANSITION}，须由 web 边界
 * {@code PolicyExceptionHandler.LEGACY_ERROR_CODES} 回查才还原成同一枚举；直接构造以免降级（EXC-06）。
 * </p>
 *
 * @author wei.sun
 * @since 2026/6/23
 */
public class PolicyStatusTransitionException extends IllegalStateTransitionException {

    public PolicyStatusTransitionException(String aggregateType, String aggregateId,
                                           String fromStatus, String toStatus) {
        super(PolicyErrorCode.POLICY_STATUS_TRANSITION_ILLEGAL, aggregateType, aggregateId,
              fromStatus, toStatus);
    }

    public PolicyStatusTransitionException(String aggregateType, String aggregateId,
                                           String fromStatus, String toStatus, String reason) {
        super(PolicyErrorCode.POLICY_STATUS_TRANSITION_ILLEGAL, aggregateType, aggregateId,
              fromStatus, toStatus, reason);
    }
}
