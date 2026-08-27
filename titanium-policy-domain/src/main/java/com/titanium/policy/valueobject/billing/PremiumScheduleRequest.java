package com.titanium.policy.valueobject.billing;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 期缴计划生成请求（保单域值对象，BILL-3）
 * <p>
 * 承保出单后为保单计费账户生成期缴计划的领域语义入参，由 {@code BillingServicePort} 的 Adapter
 * 翻译为计费域 Feign 契约。携带缴费模式、总期数、每期金额、首期日期等期缴计划生成所需参数。
 * </p>
 *
 * @param policyId          保单ID
 * @param billingAccountId  计费账户ID（来自同步开账响应，禁止写后查询异步投影）
 * @param paymentMode       缴费模式（LUMP_SUM/ANNUAL/MONTHLY）
 * @param totalPeriods      总期数（趸交固定1期）
 * @param installmentAmount 每期应缴金额
 * @param currency          币种（如 CNY）
 * @param firstDueDate      首期应缴日期
 * @param tenantId          租户ID
 */
public record PremiumScheduleRequest(String policyId, String billingAccountId, String paymentMode, int totalPeriods,
                                     BigDecimal installmentAmount, String currency, LocalDate firstDueDate,
                                     String tenantId) {

    /**
     * 兼容既有调用；新出单链路必须传入同步开账返回的计费账户ID。
     */
    public PremiumScheduleRequest(String policyId, String paymentMode, int totalPeriods,
                                  BigDecimal installmentAmount, String currency, LocalDate firstDueDate,
                                  String tenantId) {
        this(policyId, null, paymentMode, totalPeriods, installmentAmount, currency, firstDueDate, tenantId);
    }
}
