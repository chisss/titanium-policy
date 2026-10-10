package com.titanium.policy.valueobject.policy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.titanium.metadata.enums.policy.PolicyServiceRestrictionType;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceServiceRestriction;

/**
 * 保单的<b>服务受限标记清单</b>——把「多个受限种类 + 其施加与解除」内聚成一个不可变值对象。
 *
 * <p>
 * 与 {@code InsuredPartyList} 同范式：集合本身承载状态推进语义，聚合根只调一行，
 * 避免把逐类型的查找/替换/计数散进上千行的 {@code Policy} 聚合；
 * 同时它可脱离 Axon 容器直接 {@code new} 出来单测（充血模型的可测性收益）。
 * </p>
 *
 * <p>
 * 🔴 <b>施加与解除都是失败关闭</b>：重复施加同一受限种类、或对未受限的种类解除，一律拒绝而非静默幂等。
 * 幂等性由案件级 {@code requestId} 承担（同一请求重放返回既有回执，见
 * {@code Policy.handle(ApplyPolicyMaintenanceCommand)}）；能走到这里的是<b>另一个</b>请求，
 * 其语义要么非法（保单已被挂失，再挂一次无意义）、要么说明前序状态判断有误，静默通过会把
 * 「两次挂失只生效一次」这类事实悄悄抹平，事后无从对账。
 * </p>
 *
 * @param entries 受限标记条目（保留已解除条目，见 {@link PolicyServiceRestriction}）
 * @author wei.sun
 * @since 2026/9/29
 */
public record PolicyServiceRestrictions(List<PolicyServiceRestriction> entries) {

    /** 空清单（保单初始无任何服务受限）。 */
    public static final PolicyServiceRestrictions EMPTY = new PolicyServiceRestrictions(List.of());

    public PolicyServiceRestrictions {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    /**
     * 施加一条服务受限标记（荷载为 {@code APPLY} 动作）。
     *
     * @param restriction 受限事实载荷
     * @param effectiveAt 受限生效时间（取本次保全应用的生效时点）
     * @return 追加后的新清单
     */
    public PolicyServiceRestrictions restrict(
            PolicyMaintenanceServiceRestriction restriction,
            LocalDateTime effectiveAt) {
        PolicyServiceRestrictionType type = restriction.type();
        if (active(type)) {
            throw invalid("该受限种类已在生效中，不能重复施加: " + type.getCode());
        }
        List<PolicyServiceRestriction> next = new ArrayList<>(entries);
        next.add(new PolicyServiceRestriction(type, effectiveAt, null));
        return new PolicyServiceRestrictions(next);
    }

    /**
     * 解除一条服务受限标记（荷载为 {@code RELEASE} 动作）。
     *
     * @param restriction 受限事实载荷
     * @param releasedAt  解除时间（取本次保全应用的生效时点）
     * @return 置位 {@code releasedAt} 后的新清单
     */
    public PolicyServiceRestrictions release(
            PolicyMaintenanceServiceRestriction restriction,
            LocalDateTime releasedAt) {
        PolicyServiceRestrictionType type = restriction.type();
        int index = indexOfActive(type);
        if (index < 0) {
            throw invalid("该受限种类当前未受限，不能解除: " + type.getCode());
        }
        List<PolicyServiceRestriction> next = new ArrayList<>(entries);
        next.set(index, next.get(index).release(releasedAt));
        return new PolicyServiceRestrictions(next);
    }

    /**
     * 按载荷动作推进清单——把「施加还是解除」的分支收在本对象内，聚合根与调用方不必各自 switch。
     *
     * @param restriction 受限事实载荷
     * @param effectiveAt 本次保全应用的生效时点
     * @return 推进后的新清单
     */
    public PolicyServiceRestrictions apply(
            PolicyMaintenanceServiceRestriction restriction,
            LocalDateTime effectiveAt) {
        return restriction.applying()
                ? restrict(restriction, effectiveAt)
                : release(restriction, effectiveAt);
    }

    /** 保单当前是否处于任意一种服务受限之下（准入判定的总闸口）。 */
    public boolean restricted() {
        return entries.stream().anyMatch(PolicyServiceRestriction::active);
    }

    /** 指定受限种类当前是否生效。 */
    public boolean active(PolicyServiceRestrictionType type) {
        return indexOfActive(type) >= 0;
    }

    /** 指定受限种类的当前生效条目（未受限则为空）。 */
    public Optional<PolicyServiceRestriction> find(PolicyServiceRestrictionType type) {
        int index = indexOfActive(type);
        return index < 0 ? Optional.empty() : Optional.of(entries.get(index));
    }

    /** 当前生效中的受限种类清单（供审计与展示按稳定顺序读取）。 */
    public List<PolicyServiceRestrictionType> activeTypes() {
        return entries.stream()
                .filter(PolicyServiceRestriction::active)
                .map(PolicyServiceRestriction::type)
                .toList();
    }

    private int indexOfActive(PolicyServiceRestrictionType type) {
        for (int index = 0; index < entries.size(); index++) {
            PolicyServiceRestriction entry = entries.get(index);
            if (entry.active() && entry.type() == type) {
                return index;
            }
        }
        return -1;
    }

    private static PolicyBusinessRuleException invalid(String message) {
        return new PolicyBusinessRuleException(
                PolicyErrorCode.POLICY_SERVICE_RESTRICTION_INVALID, message);
    }
}
