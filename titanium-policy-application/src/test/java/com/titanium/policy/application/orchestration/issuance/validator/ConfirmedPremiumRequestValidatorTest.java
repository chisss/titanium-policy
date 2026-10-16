package com.titanium.policy.application.orchestration.issuance.validator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.titanium.common.exception.BusinessException;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.policy.valueobject.pricing.ConfirmedPremiumRequest;

/**
 * 确认保费请求校验器测试（m25-08/DSN-06 增补）。
 * <p>
 * 锁定 {@code validateSubjectInfo} 的三个边界：age 为 null 拒绝（原活校验保持，任务书所称
 * 「int age==null 恒 false 死校验」经实读证伪——{@code ConfirmedPremiumRequest.age} 是
 * {@code Integer}）；age 为负拒绝（本次增补的防御深度）；age 为 0 合法（0 岁新生儿与
 * 「未提供」必须以不同类型值区分，0 不再充当缺失默认值）。
 * </p>
 */
class ConfirmedPremiumRequestValidatorTest {

    private final ConfirmedPremiumRequestValidator validator = new ConfirmedPremiumRequestValidator();

    @Test
    void rejectsNullAge() {
        BusinessException exception = assertThrows(BusinessException.class, () -> validator.validate(request(null)));

        assertEquals(PolicyErrorCode.ISSUANCE_PREMIUM_SUBJECT_INFO_MISSING.getCode(), exception.getErrorCodeValue());
    }

    @Test
    void rejectsNegativeAge() {
        BusinessException exception = assertThrows(BusinessException.class, () -> validator.validate(request(-1)));

        assertEquals(PolicyErrorCode.ISSUANCE_PREMIUM_SUBJECT_INFO_MISSING.getCode(), exception.getErrorCodeValue());
    }

    @Test
    void acceptsZeroAgeForNewborn() {
        assertDoesNotThrow(() -> validator.validate(request(0)));
    }

    @Test
    void acceptsValidAdultAge() {
        assertDoesNotThrow(() -> validator.validate(request(35)));
    }

    private ConfirmedPremiumRequest request(Integer age) {
        return new ConfirmedPremiumRequest("REQ-1", "BIZ-1", "PRODUCT-1", "product-v3",
                LocalDateTime.of(2026, 1, 1, 0, 0), "CNY", new BigDecimal("100000"), age, "MALE",
                10, 10, 10, null, null, "TENANT-1");
    }
}
