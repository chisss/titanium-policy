package com.titanium.policy.valueobject;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.policy.common.enums.PolicyStatusCode;
import com.titanium.policy.exception.PolicyStatusTransitionException;

/**
 * 保单状态机测试
 * <p>
 * 重点验证 4B 新增的失效/复效流转：EFFECTIVE→LAPSED→EFFECTIVE，LAPSED→TERMINATED，
 * 以及非法流转拦截（满期 EXPIRED 终态不可复效）。
 * </p>
 */
class PolicyStatusTest {

    private PolicyStatus statusOf(PolicyStatusCode code) {
        return new PolicyStatus(code, LocalDateTime.now(), "init", "op");
    }

    @Test
    @DisplayName("生效→失效：宽限期满未缴费")
    void shouldLapseFromEffective() {
        PolicyStatus lapsed = statusOf(PolicyStatusCode.EFFECTIVE)
                .transitionStatus(PolicyStatusCode.LAPSED, "宽限期满未缴", "system");
        assertEquals(PolicyStatusCode.LAPSED, lapsed.statusCode());
    }

    @Test
    @DisplayName("失效→复效：补缴+核保通过")
    void shouldReinstateFromLapsed() {
        PolicyStatus reinstated = statusOf(PolicyStatusCode.LAPSED)
                .transitionStatus(PolicyStatusCode.EFFECTIVE, "复效", "op");
        assertEquals(PolicyStatusCode.EFFECTIVE, reinstated.statusCode());
    }

    @Test
    @DisplayName("失效→终止：超复效期限")
    void shouldTerminateFromLapsed() {
        assertDoesNotThrow(() -> statusOf(PolicyStatusCode.LAPSED)
                .transitionStatus(PolicyStatusCode.TERMINATED, "超复效期限", "system"));
    }

    @Test
    @DisplayName("满期为终态：EXPIRED 不可复效")
    void shouldRejectReinstateFromExpired() {
        assertThrows(PolicyStatusTransitionException.class, () -> statusOf(PolicyStatusCode.EXPIRED)
                .transitionStatus(PolicyStatusCode.EFFECTIVE, "非法复效", "op"));
    }

    @Test
    @DisplayName("未生效不可直接失效")
    void shouldRejectLapseFromNotEffective() {
        assertThrows(PolicyStatusTransitionException.class, () -> statusOf(PolicyStatusCode.NOT_EFFECTIVE)
                .transitionStatus(PolicyStatusCode.LAPSED, "非法失效", "op"));
    }
}
