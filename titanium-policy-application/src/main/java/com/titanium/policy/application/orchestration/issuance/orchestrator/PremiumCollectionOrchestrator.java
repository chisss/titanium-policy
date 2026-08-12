package com.titanium.policy.application.orchestration.issuance.orchestrator;

import java.time.LocalDateTime;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.metadata.enums.billing.BillingEnum.PaymentMethod;
import com.titanium.metadata.enums.billing.PremiumCollectionMode;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.command.RecordPremiumCollectionCommand;
import com.titanium.policy.common.constant.PolicyConstants;
import com.titanium.policy.port.BillingServicePort;
import com.titanium.policy.port.PaymentServicePort;
import com.titanium.policy.valueobject.billing.BillingResult;
import com.titanium.policy.valueobject.billing.PremiumBillRequest;
import com.titanium.policy.valueobject.payment.PaymentOrderResult;
import com.titanium.policy.valueobject.payment.PremiumPaymentRequest;
import com.titanium.policy.valueobject.policy.CollectionResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保费收费编排器（同步命令式编排）
 * <p>
 * 出单后按<b>收费方式</b>路由收款流程，补齐此前 policy 域「只开账单不收钱」的断裂：
 * </p>
 * <table border="1">
 *   <caption>五种收费方式的编排路径</caption>
 *   <tr><th>收费方式</th><th>开账单</th><th>建支付单</th><th>立即标记收讫</th><th>保单可生效</th></tr>
 *   <tr><td>FREE 免支付</td><td>✅ 零元单</td><td>—</td><td>✅</td><td>立即</td></tr>
 *   <tr><td>ONLINE 线上支付</td><td>✅</td><td>✅ 返回凭据</td><td>—</td><td>支付回调后</td></tr>
 *   <tr><td>WITHHOLD 代扣</td><td>✅</td><td>✅ 提交扣款</td><td>—</td><td>代扣回调后</td></tr>
 *   <tr><td>OFFLINE 线下</td><td>✅</td><td>—</td><td>—</td><td>财务确认收讫后</td></tr>
 *   <tr><td>PAY_AFTER_USE 先享后付</td><td>✅ 标后付</td><td>—</td><td>—（挂账）</td><td>立即（不以收讫为前提）</td></tr>
 * </table>
 * <p>
 * <b>失败语义</b>：收费失败<b>不销毁保单</b>——保单停在未生效态、账单待催缴，可重新发起收款。
 * 这与「出单失败」不同：出单是承保行为，收费是履约行为，二者失败后果不同。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PremiumCollectionOrchestrator {

    private final BillingServicePort billingServicePort;
    private final PaymentServicePort paymentServicePort;
    private final CommandGateway     commandGateway;

    /**
     * 为保单发起收费。
     *
     * @param policyId       保单ID
     * @param customerId     付款客户ID（投保人）
     * @param payableAmount  应付保费
     * @param collectionMode 收费方式
     * @param tenantId       租户ID
     * @return 收费结果（账单ID / 支付单ID / 支付凭据 / 是否已收讫）
     */
    public CollectionResult collect(String policyId, String customerId, Money payableAmount,
                                    PremiumCollectionMode collectionMode, String tenantId) {
        if (collectionMode == null) {
            log.warn("[收费编排] 保单未指定收费方式，跳过收费（保单停未生效待人工补正）: policyId={}", policyId);
            return CollectionResult.skipped("未指定收费方式");
        }

        // ① 开账单：五种方式均需账单（免支付开零元单、先享后付开后付单），账单是应收的唯一凭据
        String billId = createBill(policyId, customerId, payableAmount, tenantId);

        // ② 免支付：零元账单直接视为收讫，回写保单后即可生效
        if (collectionMode.isSettledOnIssue()) {
            log.info("[收费编排] 免支付（零元保）直接标记收讫: policyId={}, billId={}", policyId, billId);
            // 零元单一次性结清，缴费方式为趸缴（BillingEnum.PaymentMethod 语义实为缴费频率）
            recordCollection(policyId, payableAmount, PaymentMethod.LUMP_SUM, tenantId);
            return CollectionResult.settled(billId);
        }

        // ③ 先享后付：账单挂账，保单直接生效（不以收讫为生效前提），逾期走失效流程
        if (collectionMode.allowsActivationWithoutPayment()) {
            log.info("[收费编排] 先享后付：账单挂账，保单可直接生效: policyId={}, billId={}", policyId, billId);
            return CollectionResult.deferred(billId);
        }

        // ④ 线上支付/代扣：建支付单并取支付凭据
        if (collectionMode.requiresPaymentOrder()) {
            PaymentOrderResult payment = paymentServicePort.createPaymentOrder(new PremiumPaymentRequest(policyId,
                    billId, customerId, payableAmount, collectionMode, "保险费收取", tenantId));
            if (!payment.success()) {
                log.error("[收费编排] 支付单创建失败（保单停未生效，账单待催缴）: policyId={}, 原因={}", policyId,
                        payment.failureReason());
                return CollectionResult.pending(billId, null, null);
            }
            log.info("[收费编排] 支付单已创建，待支付回调: policyId={}, paymentOrderId={}", policyId,
                    payment.paymentOrderId());
            return CollectionResult.pending(billId, payment.paymentOrderId(), payment.paymentCredential());
        }

        // ⑤ 线下收费：仅开账单，等财务确认收讫后经回调回写
        log.info("[收费编排] 线下收费：账单已开立，待财务确认收讫: policyId={}, billId={}", policyId, billId);
        return CollectionResult.pending(billId, null, null);
    }

    /**
     * 开立保费账单（远程失败不阻断，返回 null 由调用方记录待补偿）。
     */
    private String createBill(String policyId, String customerId, Money payableAmount, String tenantId) {
        try {
            BillingResult result = billingServicePort
                    .createPremiumBill(new PremiumBillRequest(policyId, customerId, payableAmount, null, tenantId));
            if (result != null && result.success()) {
                return result.billId();
            }
            log.error("[收费编排] 开立保费账单失败（待补偿）: policyId={}", policyId);
            return null;
        } catch (Exception ex) {
            log.error("[收费编排] 开立保费账单异常（待补偿）: policyId={}", policyId, ex);
            return null;
        }
    }

    /**
     * 回写实收到保单（收讫后保单方满足生效的保费条件）。
     */
    private void recordCollection(String policyId, Money amount, PaymentMethod method, String tenantId) {
        commandGateway.sendAndWait(new RecordPremiumCollectionCommand(policyId, "FREE_" + policyId, null, amount,
                method, LocalDateTime.now(), PolicyConstants.POLICY_SYSTEM, tenantId));
    }
}
