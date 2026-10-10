package com.titanium.policy.valueobject.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.policy.PolicyServiceRestrictionAction;
import com.titanium.metadata.enums.policy.PolicyServiceRestrictionType;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceServiceRestriction;

/**
 * 保单服务受限标记清单值对象测试（g04-09）。
 * <p>
 * 锁死四条契约：① <b>失败关闭</b>——重复施加同一受限种类、对未受限的种类解除，一律拒绝而非静默幂等（幂等性由
 * 案件级 requestId 承担，能走到这里的是另一个请求）；② <b>解除保留条目</b>——只补 {@code releasedAt} 不删条目，
 * 使「生效时间 + 解除时间」构成一次受限生命周期的完整记录，清单推进保持单调；③ <b>可再次施加</b>——挂失→解除→
 * 再挂失是正常业务，解除后必须能重新施加（否则挂失只能办一次）；④ 清单<b>不可变</b>，每次推进返回新对象。
 * </p>
 */
class PolicyServiceRestrictionsTest {

    private static final LocalDateTime EFFECTIVE_AT = LocalDateTime.of(2026, 9, 29, 10, 0);
    private static final LocalDateTime RELEASED_AT  = EFFECTIVE_AT.plusDays(3);

    @Test
    @DisplayName("施加受限：清单转为受限，条目时点与种类可读，原清单不受影响")
    void shouldRestrictAndExposeActiveType() {
        PolicyServiceRestrictions empty = PolicyServiceRestrictions.EMPTY;

        PolicyServiceRestrictions restricted = empty.restrict(apply(), EFFECTIVE_AT);

        assertFalse(empty.restricted(), "原清单不得被就地修改（值对象不可变）");
        assertTrue(restricted.restricted(), "施加后清单即为受限");
        assertTrue(restricted.active(PolicyServiceRestrictionType.LOSS_REPORT), "该种类当前生效");
        assertEquals(List.of(PolicyServiceRestrictionType.LOSS_REPORT), restricted.activeTypes());
        PolicyServiceRestriction entry = restricted.find(PolicyServiceRestrictionType.LOSS_REPORT)
                .orElseThrow();
        assertEquals(EFFECTIVE_AT, entry.effectiveAt());
        assertTrue(entry.active(), "新施加的条目尚未解除");
    }

    @Test
    @DisplayName("解除受限：清单复归不受限，但条目保留且带上解除时间（历史可查）")
    void shouldReleaseKeepsEntryForAudit() {
        PolicyServiceRestrictions restricted = PolicyServiceRestrictions.EMPTY
                .restrict(apply(), EFFECTIVE_AT);

        PolicyServiceRestrictions released = restricted.release(release(), RELEASED_AT);

        assertFalse(released.restricted(), "解除后不再受限");
        assertTrue(released.activeTypes().isEmpty(), "生效中的受限种类清单为空");
        assertTrue(released.find(PolicyServiceRestrictionType.LOSS_REPORT).isEmpty(),
                "find 只返回当前生效条目");
        assertEquals(1, released.entries().size(),
                "🔴 解除保留条目：删除条目会让读模型与快照再也答不出「这张保单何时被挂失、何时解除」");
        assertEquals(RELEASED_AT, released.entries().getFirst().releasedAt());
        assertEquals(EFFECTIVE_AT, released.entries().getFirst().effectiveAt(), "生效时间不因解除而改写");
    }

    @Test
    @DisplayName("失败关闭：重复施加、解除未受限、重复解除一律拒绝")
    void shouldFailClosedOnDuplicateRestrictAndUngroundedRelease() {
        PolicyServiceRestrictions restricted = PolicyServiceRestrictions.EMPTY
                .restrict(apply(), EFFECTIVE_AT);

        assertThrows(PolicyBusinessRuleException.class,
                () -> restricted.restrict(apply(), EFFECTIVE_AT.plusHours(1)),
                "已受限时再次施加同一类型须拒绝——静默通过会把「两次挂失只生效一次」悄悄抹平");
        assertThrows(PolicyBusinessRuleException.class,
                () -> PolicyServiceRestrictions.EMPTY.release(release(), RELEASED_AT),
                "对未受限的种类解除须拒绝");
        assertThrows(PolicyBusinessRuleException.class,
                () -> restricted.release(release(), RELEASED_AT).release(release(), RELEASED_AT.plusDays(1)),
                "重复解除须拒绝");
    }

    @Test
    @DisplayName("🔴 解除后可再次施加：挂失→解除→再挂失是正常业务，不得被「已存在条目」挡住")
    void shouldAllowRestrictAfterRelease() {
        PolicyServiceRestrictions lifecycle = PolicyServiceRestrictions.EMPTY
                .restrict(apply(), EFFECTIVE_AT)
                .release(release(), RELEASED_AT)
                .restrict(apply(), RELEASED_AT.plusDays(1));

        assertTrue(lifecycle.restricted(), "第二次挂失后保单再次受限");
        assertEquals(2, lifecycle.entries().size(),
                "两轮受限各留一条记录，历史不互相覆盖");
        assertTrue(lifecycle.entries().getFirst().active() == false, "首轮已解除");
        assertTrue(lifecycle.entries().getLast().active(), "次轮仍在生效");
    }

    @Test
    @DisplayName("按载荷动作分派：APPLY 施加、RELEASE 解除，与显式方法结果一致")
    void shouldDispatchByActionPayload() {
        PolicyServiceRestrictions restricted = PolicyServiceRestrictions.EMPTY
                .apply(apply(), EFFECTIVE_AT);
        assertEquals(PolicyServiceRestrictions.EMPTY.restrict(apply(), EFFECTIVE_AT).entries(),
                restricted.entries(), "APPLY 载荷须推进为施加");

        PolicyServiceRestrictions released = restricted.apply(release(), RELEASED_AT);
        assertEquals(restricted.release(release(), RELEASED_AT).entries(), released.entries(),
                "RELEASE 载荷须推进为解除——分支收在值对象内，调用方不必各自 switch");
    }

    @Test
    @DisplayName("空清单归一化与不可变性：null 视为无受限，条目标量不可追加")
    void shouldNormalizeNullAndRejectMutation() {
        PolicyServiceRestrictions absent = new PolicyServiceRestrictions(null);

        assertFalse(absent.restricted(), "null 条目即无受限（存量事件无此字段时的回落语义）");
        assertTrue(absent.entries().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> absent.entries().add(new PolicyServiceRestriction(
                        PolicyServiceRestrictionType.LOSS_REPORT, EFFECTIVE_AT, null)),
                "条目清单必须不可变——可变清单会让同一次回放产生不同结果");
    }

    private static PolicyMaintenanceServiceRestriction apply() {
        return new PolicyMaintenanceServiceRestriction(
                PolicyServiceRestrictionType.LOSS_REPORT, PolicyServiceRestrictionAction.APPLY);
    }

    private static PolicyMaintenanceServiceRestriction release() {
        return new PolicyMaintenanceServiceRestriction(
                PolicyServiceRestrictionType.LOSS_REPORT, PolicyServiceRestrictionAction.RELEASE);
    }
}
