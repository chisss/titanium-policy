package com.titanium.policy.entity;

import java.time.LocalDateTime;

import com.titanium.metadata.enums.billing.BillingEnum;
import com.titanium.policy.valueobject.Amount;

public record PaymentRecord(String paymentId, String paymentNo, Amount paymentAmount, LocalDateTime paymentTime,
                            String paymentMethod, BillingEnum.ReconciliationStatus reconciliationStatus) {
    /**
     * 记录缴费信息
     * <p>
     * 同步财务域对账状态
     * </p>
     */
    public void recordPayment() {
        // 这里应该调用财务域记录缴费信息
        // 暂时省略具体实现
    }
}
