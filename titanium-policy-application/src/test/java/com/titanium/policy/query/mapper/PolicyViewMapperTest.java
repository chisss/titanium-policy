package com.titanium.policy.query.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.common.enums.PremiumPaymentCycle;
import com.titanium.policy.common.enums.PremiumPaymentMethod;
import com.titanium.policy.common.enums.PremiumPaymentStatus;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.query.view.PolicyView;
import com.titanium.policy.valueobject.PremiumPlan;

/**
 * 保单创建投影映射器测试（{@link PolicyViewMapper#applyCreated}）。
 * <p>
 * 重点守护「缴费方式」列：映射器 {@code unmappedTargetPolicy} 为 {@code IGNORE}，映射源写错时 MapStruct
 * 只会**静默漏字段**、不报编译错——读模型该列将永远为空，即缺口 G7「写通读不通」的复发形态。
 * 故对新增列直接断言生成实现（{@link PolicyViewMapperImpl}）的真实映射结果，而非只测处理器。
 * </p>
 */
class PolicyViewMapperTest {

    private final PolicyViewMapper mapper = new PolicyViewMapperImpl();

    @Test
    void mapsPaymentMethodCodeFromPremiumPlan() {
        PolicyView view = new PolicyView();

        mapper.applyCreated(view, createdEvent(new PremiumPlan(
                Money.of(new BigDecimal("1000"), "CNY"), PremiumPaymentMethod.INSTALLMENT_PAYMENT,
                PremiumPaymentCycle.MONTHLY, LocalDateTime.parse("2026-09-01T00:00:00"),
                PremiumPaymentStatus.UNPAID)));

        assertEquals(PremiumPaymentMethod.INSTALLMENT_PAYMENT.getCode(), view.getPaymentMethod());
    }

    @Test
    void keepsPaymentMethodUntouchedWhenPremiumPlanAbsent() {
        PolicyView view = new PolicyView();
        view.setPaymentMethod(PremiumPaymentMethod.SINGLE_PAYMENT.getCode());

        mapper.applyCreated(view, createdEvent(null));

        // NullValuePropertyMappingStrategy.IGNORE：事件缺保费计划时不得把既有列清零
        assertEquals(PremiumPaymentMethod.SINGLE_PAYMENT.getCode(), view.getPaymentMethod());
    }

    @Test
    void leavesPaymentMethodNullWhenPremiumPlanCarriesNoMethod() {
        PolicyView view = new PolicyView();

        mapper.applyCreated(view, createdEvent(
                new PremiumPlan(Money.of(new BigDecimal("1000"), "CNY"), null, null, null, null)));

        assertNull(view.getPaymentMethod());
    }

    /** 最小出单事件：本组断言只涉及保单号与保费计划，其余字段留空。 */
    private PolicyCreatedEvent createdEvent(PremiumPlan premiumPlan) {
        return new PolicyCreatedEvent("POLICY_001", null, null, null, null, null, null, null, null,
                null, null, null, premiumPlan, null, null, null, null, null, "TENANT_001");
    }
}
