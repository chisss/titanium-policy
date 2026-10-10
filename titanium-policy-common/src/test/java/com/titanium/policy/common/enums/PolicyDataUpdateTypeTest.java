package com.titanium.policy.common.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 保全项枚举机械判据（G12/g12-06 增员/减员入口）
 * <p>
 * 守三件事：① 增员/减员两项的 code 反查闭环（保全落库后回读一致）；② maintenance 保全项编码 →
 * 批改类型映射命中新增两项（保全域受理进入保单批改链路的唯一入口，删 case 即失配）；
 * ③ R-4 业务确认前的暂定标志位显式锁定——业务确认后调整口径时本类是对照点，防静默漂移。
 * </p>
 */
class PolicyDataUpdateTypeTest {

    @Test
    void resolvesMemberTypesByCode() {
        assertEquals(PolicyDataUpdateType.INSURED_MEMBER_ADD,
                PolicyDataUpdateType.fromCode("INSURED_MEMBER_ADD"));
        assertEquals(PolicyDataUpdateType.INSURED_MEMBER_REMOVE,
                PolicyDataUpdateType.fromCode("INSURED_MEMBER_REMOVE"));
    }

    @Test
    void mapsMaintenanceItemCodesToMemberTypes() {
        assertEquals(PolicyDataUpdateType.INSURED_MEMBER_ADD,
                PolicyDataUpdateType.byMaintenanceType("INSURED_MEMBER_ADD"));
        assertEquals(PolicyDataUpdateType.INSURED_MEMBER_REMOVE,
                PolicyDataUpdateType.byMaintenanceType("INSURED_MEMBER_REMOVE"));
    }

    @Test
    void memberTypesNeverChangePolicyStatus() {
        assertFalse(PolicyDataUpdateType.INSURED_MEMBER_ADD.changesStatus());
        assertFalse(PolicyDataUpdateType.INSURED_MEMBER_REMOVE.changesStatus());
    }

    @Test
    void memberTypeFlagsMirrorSumInsuredSemanticsBeforeBusinessConfirmation() {
        // 🔴 R-4 前暂定口径：增员镜像加保（需核保 + 保费重算），减员镜像减保（不核保 + 保费重算）
        assertTrue(PolicyDataUpdateType.INSURED_MEMBER_ADD.needsUnderwriting());
        assertTrue(PolicyDataUpdateType.INSURED_MEMBER_ADD.needsPremiumRecalc());
        assertFalse(PolicyDataUpdateType.INSURED_MEMBER_REMOVE.needsUnderwriting());
        assertTrue(PolicyDataUpdateType.INSURED_MEMBER_REMOVE.needsPremiumRecalc());
    }

    @Test
    void statusFamilyMaintenanceCodesStayUnmapped() {
        // 状态类保全走 4A/4B 状态机，不属批改（既有语义回归）
        assertNull(PolicyDataUpdateType.byMaintenanceType("POLICY_SUSPENSION"));
        assertNull(PolicyDataUpdateType.byMaintenanceType(null));
    }
}
