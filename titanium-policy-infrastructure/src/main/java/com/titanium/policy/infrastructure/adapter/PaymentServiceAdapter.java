package com.titanium.policy.infrastructure.adapter;

import org.springframework.stereotype.Component;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.metadata.valueobject.Money;
import com.titanium.payment.api.PaymentApi;
import com.titanium.payment.api.request.CancelPaymentRequest;
import com.titanium.payment.api.request.CreatePaymentOrderRequest;
import com.titanium.payment.api.response.PaymentOrderResponse;
import com.titanium.policy.port.PaymentServicePort;
import com.titanium.policy.valueobject.payment.PaymentOrderResult;
import com.titanium.policy.valueobject.payment.PremiumPaymentRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 支付服务适配器
 * <p>
 * {@link PaymentServicePort} 的基础设施实现，调用支付域 {@link PaymentApi}（Feign）并把支付域
 * Response 翻译为保单域防腐值对象。
 * </p>
 * <p>
 * <b>失败不抛异常</b>：收费失败不应销毁已承保的保单——保单停在未生效态、账单待催缴、可重新
 * 发起收款。故远程异常在此转为 {@link PaymentOrderResult#failed(String)}，由收费编排决定后续。
 * </p>
 * <p>
 * 🔴 <b>支付凭据待专项落地</b>：payment 域现有 {@code PaymentOrderResponse} 未含支付凭据字段
 * （支付链接/二维码/预支付串），线上支付的前端唤起能力属专项 PAY-SP。本适配器暂以支付单ID
 * 占位并记录告警，契约字段已就位，专项落地时无需改 Port 与出单链路。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentServiceAdapter implements PaymentServicePort {

    /** 系统操作人标识（保单域自动发起的收费动作，无人工操作人） */
    private static final String POLICY_SYSTEM_OPERATOR = "POLICY_SYSTEM";

    private final PaymentApi paymentApi;

    @Override
    public PaymentOrderResult createPaymentOrder(PremiumPaymentRequest request) {
        try {
            CreatePaymentOrderRequest apiRequest = new CreatePaymentOrderRequest();
            apiRequest.setPolicyId(request.policyId());
            apiRequest.setCustomerId(request.customerId());
            apiRequest.setAmount(request.amount() != null ? request.amount().value() : null);
            apiRequest.setCurrency(request.amount() != null ? request.amount().currency() : null);
            apiRequest.setPaymentMethod(toPaymentMethod(request));
            apiRequest.setDescription(request.description());

            ApiResponse<PaymentOrderResponse> response = paymentApi.createPaymentOrder(apiRequest, request.tenantId());
            if (response == null || !response.isSuccess() || response.getData() == null) {
                String reason = response != null ? response.getMessage() : "支付域响应为空";
                log.error("创建保费支付单失败: policyId={}, 原因={}", request.policyId(), reason);
                return PaymentOrderResult.failed(reason);
            }
            PaymentOrderResponse order = response.getData();
            log.info("保费支付单创建成功: policyId={}, paymentOrderId={}, 金额={}", request.policyId(),
                    order.getOrderId(), order.getAmount());
            // TODO(PAY-SP): payment 域补支付凭据字段后改为透传真实凭据（支付链接/二维码/预支付串）
            log.warn("支付凭据暂以支付单ID占位（PAY-SP 专项落地真实网关凭据）: paymentOrderId={}", order.getOrderId());
            return PaymentOrderResult.succeeded(order.getOrderId(), toMoney(order), order.getStatus(),
                    order.getOrderId());
        } catch (Exception ex) {
            log.error("创建保费支付单异常（不阻断出单，保单停未生效待催缴）: policyId={}", request.policyId(), ex);
            return PaymentOrderResult.failed("支付域调用异常: " + ex.getMessage());
        }
    }

    @Override
    public PaymentOrderResult queryPaymentStatus(String paymentOrderId, String tenantId) {
        try {
            ApiResponse<PaymentOrderResponse> response = paymentApi.getPaymentOrder(paymentOrderId, tenantId);
            if (response == null || !response.isSuccess() || response.getData() == null) {
                return PaymentOrderResult.failed(response != null ? response.getMessage() : "支付域响应为空");
            }
            PaymentOrderResponse order = response.getData();
            return PaymentOrderResult.succeeded(order.getOrderId(), toMoney(order), order.getStatus(), null);
        } catch (Exception ex) {
            log.error("查询支付单状态异常: paymentOrderId={}", paymentOrderId, ex);
            return PaymentOrderResult.failed("支付域调用异常: " + ex.getMessage());
        }
    }

    @Override
    public void cancelPaymentOrder(String paymentOrderId, String reason, String tenantId) {
        try {
            CancelPaymentRequest request = new CancelPaymentRequest();
            request.setCancelReason(reason);
            request.setCancelledBy(POLICY_SYSTEM_OPERATOR);
            paymentApi.cancelPayment(paymentOrderId, request, tenantId);
            log.info("支付单已取消: paymentOrderId={}, 原因={}", paymentOrderId, reason);
        } catch (Exception ex) {
            log.error("取消支付单异常（需人工核查，避免重复收款）: paymentOrderId={}", paymentOrderId, ex);
        }
    }

    /**
     * 收费方式 → 支付方式 code。
     * <p>
     * 收费方式是「怎么收」的业务约定（线下/线上/代扣），支付方式是「走哪个通道」的技术选择。
     * 本期做最小映射：线上与代扣分别对应支付域的默认通道，具体通道选择（微信/支付宝/银联）
     * 属 PAY-SP 专项。
     * </p>
     */
    private String toPaymentMethod(PremiumPaymentRequest request) {
        if (request.collectionMode() == null) {
            return null;
        }
        return switch (request.collectionMode()) {
            case ONLINE -> "ONLINE_GATEWAY";
            case WITHHOLD -> "BANK_WITHHOLD";
            case OFFLINE -> "BANK_TRANSFER";
            case FREE, PAY_AFTER_USE -> null;
        };
    }

    /**
     * 支付域金额 + 币种 → 金额值对象（空安全）。
     */
    private Money toMoney(PaymentOrderResponse order) {
        if (order.getAmount() == null) {
            return null;
        }
        return Money.of(order.getAmount(), order.getCurrency() != null ? order.getCurrency() : "CNY");
    }
}
