package com.titanium.policy.infrastructure.adapter;

import org.springframework.stereotype.Component;

import com.titanium.billing.api.BillApi;
import com.titanium.billing.api.BillingAccountApi;
import com.titanium.billing.api.request.account.GenerateScheduleRequest;
import com.titanium.billing.api.request.bill.CreateBillRequest;
import com.titanium.billing.api.response.account.BillingAccountResponse;
import com.titanium.billing.api.response.bill.BillResponse;
import com.titanium.common.exception.BusinessException;
import com.titanium.metadata.response.ApiResponse;
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
            return new BillingResult(false, null, null);
        }
        return new BillingResult(true, response.getData().getBillId(), response.getData().getBillingAccountId());
    }

    @Override
    public void generatePremiumSchedule(PremiumScheduleRequest request) {
        log.info("为保单生成期缴计划: policyId={}, paymentMode={}, totalPeriods={}", request.policyId(),
                request.paymentMode(), request.totalPeriods());

        String accountId = request.billingAccountId();
        if (accountId == null || accountId.isBlank()) {
            throw new BusinessException("查询计费账户失败: policyId=" + request.policyId() + ", accountId为空");
        }

        GenerateScheduleRequest scheduleRequest = new GenerateScheduleRequest();
        scheduleRequest.setPaymentMode(request.paymentMode());
        scheduleRequest.setTotalPeriods(request.totalPeriods());
        scheduleRequest.setInstallmentAmount(request.installmentAmount());
        scheduleRequest.setCurrency(request.currency());
        scheduleRequest.setFirstDueDate(request.firstDueDate());
        scheduleRequest.setOperatedBy("SYSTEM_ISSUANCE");
        scheduleRequest.setTenantId(request.tenantId());
        ApiResponse<BillingAccountResponse> scheduleResponse = generateSchedule(request.policyId(), accountId,
                scheduleRequest);
        if (scheduleResponse == null || !scheduleResponse.isSuccess() || scheduleResponse.getData() == null) {
            String message = scheduleResponse != null ? scheduleResponse.getMessage() : "无响应";
            throw new BusinessException("生成期缴计划失败: policyId=" + request.policyId() + ", accountId="
                    + accountId + ", message=" + message);
        }

        log.info("期缴计划生成成功: policyId={}, accountId={}, mode={}, periods={}", request.policyId(), accountId,
                request.paymentMode(), request.totalPeriods());
    }

    private ApiResponse<BillingAccountResponse> generateSchedule(String policyId, String accountId,
                                                                  GenerateScheduleRequest request) {
        try {
            return billingAccountApi.generateSchedule(accountId, request);
        } catch (RuntimeException ex) {
            throw new BusinessException("生成期缴计划异常: policyId=" + policyId + ", accountId=" + accountId, ex);
        }
    }
}
