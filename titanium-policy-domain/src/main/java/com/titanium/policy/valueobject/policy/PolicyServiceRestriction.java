package com.titanium.policy.valueobject.policy;

import java.time.LocalDateTime;

import com.titanium.metadata.enums.policy.PolicyServiceRestrictionType;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.policy.exception.PolicyBusinessRuleException;

/**
 * 保单上的一条<b>服务受限标记</b>：某类服务自 {@code effectiveAt} 起对这张保单关闭入口，
 * 至 {@code releasedAt} 为止（{@code null} 表示仍在受限中）。
 *
 * <p>
 * 受限标记<b>不改保单状态</b>（挂失保单仍是 {@code EFFECTIVE}，合同未中止），它是与状态机正交的另一条维度，
 * 见 {@link PolicyServiceRestrictionType} 的类注释。
 * </p>
 *
 * <p>
 * 🔴 <b>解除保留条目、只补 {@code releasedAt}，不从清单里删除</b>：一旦删除，「这张保单历史上是否被挂失过、
 * 何时解除」在当前状态里就只剩事件流可查，而读模型、快照与准入判定都只看当前状态。保留条目使
 * 「生效时间 + 解除时间」成为一次受限生命周期的完整记录，也让 {@link PolicyServiceRestrictions} 的
 * 状态推进保持<b>单调</b>（只追加与置位，不删除），回放确定性更易论证。
 * </p>
 *
 * @param type        受限种类
 * @param effectiveAt 受限生效时间
 * @param releasedAt  受限解除时间；{@code null} 表示仍在受限中
 * @author wei.sun
 * @since 2026/9/29
 */
public record PolicyServiceRestriction(
        PolicyServiceRestrictionType type,
        LocalDateTime effectiveAt,
        LocalDateTime releasedAt) {

    public PolicyServiceRestriction {
        if (type == null || effectiveAt == null) {
            throw invalid("服务受限标记必须包含受限种类与生效时间");
        }
        if (releasedAt != null && releasedAt.isBefore(effectiveAt)) {
            throw invalid("服务受限标记的解除时间不能早于生效时间");
        }
    }

    /** 本标记当前是否仍然生效（未被解除）。 */
    public boolean active() {
        return releasedAt == null;
    }

    /** 返回本标记的已解除副本。 */
    public PolicyServiceRestriction release(LocalDateTime at) {
        if (at == null) {
            throw invalid("解除服务受限必须给出解除时间");
        }
        if (!active()) {
            throw invalid("服务受限标记已解除，不能重复解除: " + type.getCode());
        }
        return new PolicyServiceRestriction(type, effectiveAt, at);
    }

    private static PolicyBusinessRuleException invalid(String message) {
        return new PolicyBusinessRuleException(
                PolicyErrorCode.POLICY_SERVICE_RESTRICTION_INVALID, message);
    }
}
