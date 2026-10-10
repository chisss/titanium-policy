package com.titanium.policy.application.orchestration.issuance.orchestrator;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.port.billing.BillingServicePort;
import com.titanium.policy.valueobject.billing.PremiumScheduleRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 出单期缴计划编排器。
 * <p>
 * 统一一步出单与 Saga 出单的期数换算和失败语义。账单创建成功后调用；生成失败只进入补偿路径，
 * 不回滚已经成立的承保事实。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PremiumScheduleOrchestrator {

    private static final Map<String, Integer> PERIODS_PER_YEAR = Map.of(
            "LUMP_SUM", 0,
            "ANNUAL", 1,
            "SEMI_ANNUAL", 2,
            "QUARTERLY", 4,
            "MONTHLY", 12);

    private final BillingServicePort billingServicePort;

    /**
     * 为已创建账单的保单生成期缴计划。
     *
     * @param policyId           保单ID
     * @param billId             已创建的账单ID；为空时不生成
     * @param paymentMode        缴费模式
     * @param premiumPaymentYears 缴费年数
     * @param payablePremium     应付总保费
     * @param firstDueDate       首期应缴日期
     * @param tenantId           租户ID
     */
    public void generate(String policyId, String billId, String billingAccountId, String paymentMode,
                         int premiumPaymentYears, Money payablePremium, LocalDate firstDueDate, String tenantId) {
        if (billId == null || billId.isBlank() || paymentMode == null || paymentMode.isBlank()
                || premiumPaymentYears <= 0 || payablePremium == null) {
            return;
        }
        if (billingAccountId == null || billingAccountId.isBlank()) {
            log.error("期缴计划缺少同步计费账户ID（待补偿）: policyId={}, billId={}", policyId, billId);
            return;
        }
        Integer periodsPerYear = PERIODS_PER_YEAR.get(paymentMode);
        if (periodsPerYear == null) {
            log.error("期缴计划缴费模式不受支持（待人工补偿）: policyId={}, paymentMode={}", policyId, paymentMode);
            return;
        }
        try {
            int totalPeriods = periodsPerYear == 0 ? 1 : Math.multiplyExact(premiumPaymentYears, periodsPerYear);
            LocalDate dueDate = firstDueDate != null ? firstDueDate : LocalDate.now();
            // 只做期数换算，不做整除：分期舍入（FLOOR 分摊）与末期尾差承接是计费域 PremiumSchedule 的统一职责，
            // 上游自行 HALF_UP 整除会把尾差提前磨平、与计费域分账口径脱钩。
            billingServicePort.generatePremiumSchedule(new PremiumScheduleRequest(policyId, billingAccountId,
                    paymentMode, totalPeriods, payablePremium.value(), payablePremium.currency(), dueDate, tenantId));
            log.info("期缴计划生成完成: policyId={}, billId={}, mode={}, periods={}, totalPremium={}", policyId,
                    billId, paymentMode, totalPeriods, payablePremium.value());
        } catch (RuntimeException exception) {
            log.error("期缴计划生成失败（保单和账单保留，待补偿）: policyId={}, billId={}", policyId, billId,
                    exception);
        }
    }
}
