package com.titanium.policy.entity.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.insurance.InsuranceMandateType;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;

/**
 * 险别谓词测试（G11/g11-04 ⑦ / AC-10）：{@link PolicyProduct#isCompulsory()} 是「交强不得
 * 单独解绑」守卫的唯一判据，正反与空值语义须逐条锁定。
 */
class PolicyProductMandateTest {

    /** 正例：法定强制段（交强险）为强制。 */
    @Test
    void compulsoryLineIsCompulsory() {
        assertTrue(product(InsuranceMandateType.COMPULSORY).isCompulsory());
    }

    /** 反例：自愿投保段（商业车险）非强制。 */
    @Test
    void voluntaryLineIsNotCompulsory() {
        assertFalse(product(InsuranceMandateType.VOLUNTARY).isCompulsory());
    }

    /**
     * 🔴 空值语义：{@code null}（产品未声明险别）按**非强制**——存量事件与全部非车险产品均未声明，
     * 若把 null 当强制会让它们全被守卫误拒。
     */
    @Test
    void undeclaredMandateIsNotCompulsory() {
        assertFalse(product(null).isCompulsory());
    }

    private static PolicyProduct product(InsuranceMandateType mandate) {
        return new PolicyProduct("LINE_1", 1, ProductCategory.MAIN, null, "PRODUCT_1", "P001", "测试主险",
                "V1.0", "PLAN_V1", null, null, null, null, null, null, null,
                List.of(), List.of(), List.of(), mandate);
    }
}
