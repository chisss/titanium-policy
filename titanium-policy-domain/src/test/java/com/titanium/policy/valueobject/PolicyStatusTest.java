package com.titanium.policy.valueobject;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.policy.exception.PolicyStatusTransitionException;

/**
 * 保单状态机测试
 * <p>
 * 重点验证 4B 新增的失效/复效流转：EFFECTIVE→LAPSED→EFFECTIVE，LAPSED→TERMINATED，
 * 以及非法流转拦截（满期 EXPIRED 终态不可复效）。
 * </p>
 */
class PolicyStatusTest {

    private PolicyStatus statusOf(PolicyStatus.StatusCode code) {
        return new PolicyStatus(code, LocalDateTime.now(), "init", "op");
    }

    @Test
    @DisplayName("生效→失效：宽限期满未缴费")
    void shouldLapseFromEffective() {
        PolicyStatus lapsed = statusOf(PolicyStatus.StatusCode.EFFECTIVE)
                .transitionStatus(PolicyStatus.StatusCode.LAPSED, "宽限期满未缴", "system");
        assertEquals(PolicyStatus.StatusCode.LAPSED, lapsed.statusCode());
    }

    @Test
    @DisplayName("失效→复效：补缴+核保通过")
    void shouldReinstateFromLapsed() {
        PolicyStatus reinstated = statusOf(PolicyStatus.StatusCode.LAPSED)
                .transitionStatus(PolicyStatus.StatusCode.EFFECTIVE, "复效", "op");
        assertEquals(PolicyStatus.StatusCode.EFFECTIVE, reinstated.statusCode());
    }

    @Test
    @DisplayName("失效→终止：超复效期限")
    void shouldTerminateFromLapsed() {
        assertDoesNotThrow(() -> statusOf(PolicyStatus.StatusCode.LAPSED)
                .transitionStatus(PolicyStatus.StatusCode.TERMINATED, "超复效期限", "system"));
    }

    @Test
    @DisplayName("满期为终态：EXPIRED 不可复效")
    void shouldRejectReinstateFromExpired() {
        assertThrows(PolicyStatusTransitionException.class, () -> statusOf(PolicyStatus.StatusCode.EXPIRED)
                .transitionStatus(PolicyStatus.StatusCode.EFFECTIVE, "非法复效", "op"));
    }

    @Test
    @DisplayName("未生效不可直接失效")
    void shouldRejectLapseFromNotEffective() {
        assertThrows(PolicyStatusTransitionException.class, () -> statusOf(PolicyStatus.StatusCode.NOT_EFFECTIVE)
                .transitionStatus(PolicyStatus.StatusCode.LAPSED, "非法失效", "op"));
    }
}
