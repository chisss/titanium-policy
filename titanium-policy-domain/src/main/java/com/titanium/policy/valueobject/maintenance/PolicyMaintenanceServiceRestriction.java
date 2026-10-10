package com.titanium.policy.valueobject.maintenance;

import com.titanium.metadata.enums.policy.PolicyServiceRestrictionAction;
import com.titanium.metadata.enums.policy.PolicyServiceRestrictionType;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.policy.exception.PolicyBusinessRuleException;

/**
 * Policy 聚合据以变更<b>服务受限标记</b>的受限事实（受限种类 + 受限动作）。
 *
 * <p>
 * 与 {@link PolicyMaintenanceFieldChange}（字段变更）、{@link PolicyMaintenanceAmountFact}（金额事实）并列的
 * 又一种保成效力载荷。挂失/解除挂失既不改合同要素、也不动合同状态，其效力<b>就是这条受限标记本身</b>——
 * 若只落一条无载荷的批单，事后无从回答「这张保单当时因何、对哪些服务关闭了入口」。
 * </p>
 *
 * <p>
 * 🔴 <b>生效时刻不在本对象里</b>：受限的生效时点恒取本次保全应用的 {@code effectiveAt}（追溯/未来生效由
 * 案件的生效时态决定）。另立一个时间字段会造出两处可各自漂移的时点，且它与请求摘要里已有的
 * {@code effectiveAt} 重复计入哈希。
 * </p>
 *
 * <p>
 * 🔴 <b>构造期不变量是「拒绝无意义载荷」的最后一道闸口，不是调用方约定</b>：受限种类不可为空，
 * 受限动作必须是 {@link PolicyServiceRestrictionAction#APPLY} 或 {@link PolicyServiceRestrictionAction#RELEASE}
 * ——{@code NONE} 表示「本次不发生受限变更」，其正确表达方式是<b>整个载荷为 null</b>，
 * 放行一个 {@code NONE} 载荷等于写入一条「登记了但什么也没变」的受限记录。
 * </p>
 *
 * <p>
 * 🔴 <b>不含 {@code itemCode}</b>：批改类型由保全项编码推导，而受限类保全在
 * {@code PolicyDataUpdateType} 中无专属取值（统一落 {@code POLICY_INFO_CHANGE}/INFO 兜底）。
 * 带一个推导结果为常量、不产生任何分支差异的字段，只会造出「看起来在参与决策、实际恒不参与」的
 * 死字段（与 m16-1902 批单字段明细全滤空同形的静默失效面）。将来若需按保全项细分批改类型，
 * 再连同 {@code PolicyDataUpdateType} 的取值一并补入。
 * </p>
 *
 * @param type   受限种类（挂失受限等）
 * @param action 受限动作（施加/解除）
 * @author wei.sun
 * @since 2026/9/29
 */
public record PolicyMaintenanceServiceRestriction(
        PolicyServiceRestrictionType type,
        PolicyServiceRestrictionAction action) {

    public PolicyMaintenanceServiceRestriction {
        if (type == null) {
            throw invalid("服务受限事实必须指明受限种类");
        }
        if (action == null || !action.changesRestriction()) {
            throw invalid("服务受限事实必须给出施加或解除动作");
        }
    }

    /** 本载荷是否为施加受限。 */
    public boolean applying() {
        return action == PolicyServiceRestrictionAction.APPLY;
    }

    private static PolicyBusinessRuleException invalid(String message) {
        return new PolicyBusinessRuleException(
                PolicyErrorCode.POLICY_SERVICE_RESTRICTION_INVALID, message);
    }
}
