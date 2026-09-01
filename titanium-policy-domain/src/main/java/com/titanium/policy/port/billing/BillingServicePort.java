package com.titanium.policy.port;

import com.titanium.policy.valueobject.billing.BillingResult;
import com.titanium.policy.valueobject.billing.PremiumBillRequest;
import com.titanium.policy.valueobject.billing.PremiumScheduleRequest;

/**
 * 计费服务网关端口（driven port，与聚合平级）
 * <p>
 * 承保出单后经此端口向计费域开立首期保费账单，由基础设施 Adapter 实现具体通信（当前同步 Feign 调 billing）。
 * 端口面向领域语义（入参 {@link PremiumBillRequest}、出参 {@link BillingResult}），屏蔽计费域 DTO 细节，
 * 与 {@link UnderwritingDecisionGateway} 同构。
 * </p>
 */
public interface BillingServicePort {

    /**
     * 为保单开立首期保费账单。
     *
     * @param request 保费账单请求
     * @return 计费结果
     */
    BillingResult createPremiumBill(PremiumBillRequest request);

    /**
     * 为保单生成期缴计划（BILL-3）
     * <p>
     * 出单后根据缴费模式、缴费年数、保费金额生成期缴计划，供后续定时收费任务扫描执行。
     * </p>
     *
     * @param request 期缴计划生成请求
     */
    void generatePremiumSchedule(PremiumScheduleRequest request);
}
