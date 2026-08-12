package com.titanium.policy.port;

import com.titanium.policy.valueobject.payment.PaymentOrderResult;
import com.titanium.policy.valueobject.payment.PremiumPaymentRequest;

/**
 * 支付服务端口（driven port，与聚合平级）
 * <p>
 * 出单收费编排经此端口向支付域发起保费收款。此前 policy 域<b>完全没有支付概念</b>——出单 Saga
 * 只向 billing 开账单却不收钱，而保单生效要求首期已缴，形成「开了单收不到钱、或未收钱就生效」
 * 的断裂。本端口补齐这一环。
 * </p>
 * <p>
 * <b>与 {@link BillingServicePort} 的分工</b>：billing 管「应收多少」（账单、期缴计划、对账口径），
 * payment 管「实收怎么收」（支付通道、凭据、回调）。收费编排先开账单再建支付单，二者以
 * {@code billId} 关联。
 * </p>
 * <p>
 * 🔴 <b>真实支付通道对接属专项 PAY-SP</b>：本期 Adapter 调 payment 域现有 API，网关对接、
 * 银行代扣签约、先享后付风控由该专项落地。
 * </p>
 */
public interface PaymentServicePort {

    /**
     * 创建保费支付单并发起收款。
     * <p>
     * 线上支付返回支付凭据（前端凭此唤起支付）；代扣提交扣款任务；线下与免支付不应调用本方法
     * （由收费编排依 {@code collectionMode.requiresPaymentOrder()} 判定）。
     * </p>
     *
     * @param request 保费支付请求
     * @return 支付单结果；远程失败时返回失败结果而非抛异常（收费失败不阻断出单）
     */
    PaymentOrderResult createPaymentOrder(PremiumPaymentRequest request);

    /**
     * 查询支付单状态（收费回调缺失时的兜底轮询）。
     *
     * @param paymentOrderId 支付单ID
     * @param tenantId       租户ID
     * @return 支付单结果；查询失败返回失败结果
     */
    PaymentOrderResult queryPaymentStatus(String paymentOrderId, String tenantId);

    /**
     * 取消支付单（出单流程终止或超时未支付时释放）。
     *
     * @param paymentOrderId 支付单ID
     * @param reason         取消原因
     * @param tenantId       租户ID
     */
    void cancelPaymentOrder(String paymentOrderId, String reason, String tenantId);
}
