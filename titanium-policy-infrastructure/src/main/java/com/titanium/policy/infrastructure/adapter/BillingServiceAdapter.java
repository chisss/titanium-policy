package com.titanium.policy.infrastructure.adapter;

import org.springframework.stereotype.Component;

import com.titanium.billing.api.BillApi;
import com.titanium.billing.api.BillingAccountApi;
import com.titanium.billing.api.request.CreateBillRequest;
import com.titanium.billing.api.request.GenerateScheduleRequest;
import com.titanium.billing.api.response.ApiResponse;
import com.titanium.billing.api.response.BillResponse;
import com.titanium.billing.api.response.BillingAccountResponse;
import com.titanium.policy.infrastructure.adapter.mapper.BillingRequestMapper;
import com.titanium.policy.port.BillingServicePort;
import com.titanium.policy.valueobject.billing.BillingResult;
import com.titanium.policy.valueobject.billing.PremiumBillRequest;
import com.titanium.policy.valueobject.billing.PremiumScheduleRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 计费服务适配器（driven adapter，位于 infrastructure）
 * <p>
 * {@link BillingServicePort} 的基础设施实现，承保出单后经计费域 {@link BillApi}（Feign）开立首期保费账单，
 * 并把计费域返回防腐翻译为保单域值对象 {@link BillingResult}。与 {@code SyncUnderwritingDecisionAdapter} 同构。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillingServiceAdapter implements BillingServicePort {

    private final BillApi              billApi;
    private final BillingAccountApi    billingAccountApi;
    private final BillingRequestMapper billingRequestMapper;

    @Override
    public BillingResult createPremiumBill(PremiumBillRequest request) {
        log.info("承保出单后开立首期保费账单: policyId={}, tenantId={}", request.policyId(), request.tenantId());
        // 值对象 → Feign 契约的字段映射统一收敛到 MapStruct，消除逐字段 set（tenantId 同名自动映射）
        CreateBillRequest billRequest = billingRequestMapper.toCreateBillRequest(request);

        ApiResponse<BillResponse> response = billApi.createBill(billRequest);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            log.error("开立保费账单失败: policyId={}, message={}", request.policyId(),
                    response != null ? response.getMessage() : "无响应");
            return new BillingResult(false, null);
        }
        return new BillingResult(true, response.getData().getBillId());
    }

    @Override
    public void generatePremiumSchedule(PremiumScheduleRequest request) {
        log.info("为保单生成期缴计划: policyId={}, paymentMode={}, totalPeriods={}", request.policyId(),
                request.paymentMode(), request.totalPeriods());

        try {
            // 1. 根据 policyId 查询计费账户
            ApiResponse<BillingAccountResponse> accountResp = billingAccountApi
                    .getBillingAccountByPolicyId(request.policyId());
            if (accountResp == null || !accountResp.isSuccess() || accountResp.getData() == null) {
                log.error("查询计费账户失败: policyId={}, message={}", request.policyId(),
                        accountResp != null ? accountResp.getMessage() : "无响应");
                return;
            }

            String accountId = accountResp.getData().getAccountId();

            // 2. 调用生成期缴计划接口
            GenerateScheduleRequest scheduleRequest = new GenerateScheduleRequest();
            scheduleRequest.setPaymentMode(request.paymentMode());
            scheduleRequest.setTotalPeriods(request.totalPeriods());
            scheduleRequest.setInstallmentAmount(request.installmentAmount());
            scheduleRequest.setCurrency(request.currency());
            scheduleRequest.setFirstDueDate(request.firstDueDate());
            scheduleRequest.setOperatedBy("SYSTEM_ISSUANCE");
            scheduleRequest.setTenantId(request.tenantId());

            ApiResponse<BillingAccountResponse> scheduleResp = billingAccountApi.generateSchedule(accountId,
                    scheduleRequest);
            if (scheduleResp == null || !scheduleResp.isSuccess()) {
                log.error("生成期缴计划失败: policyId={}, accountId={}, message={}", request.policyId(), accountId,
                        scheduleResp != null ? scheduleResp.getMessage() : "无响应");
                return;
            }

            log.info("期缴计划生成成功: policyId={}, accountId={}, mode={}, periods={}", request.policyId(), accountId,
                    request.paymentMode(), request.totalPeriods());
        } catch (Exception e) {
            log.error("生成期缴计划异常: policyId={}", request.policyId(), e);
        }
    }
}
