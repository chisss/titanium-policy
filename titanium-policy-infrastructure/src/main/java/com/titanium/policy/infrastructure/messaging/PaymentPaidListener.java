package com.titanium.policy.infrastructure.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSONObject;

import com.titanium.metadata.enums.BusinessDomainType;
import com.titanium.metadata.topic.CrossDomainTopics;
import com.titanium.policy.application.orchestration.payment.PremiumCollectionResultOrchestrator;
import com.titanium.policy.infrastructure.messaging.inbound.PaymentOrderPaidMessage;
import com.titanium.policy.infrastructure.messaging.mapper.PolicyPaymentResultMapper;
import com.titanium.policy.valueobject.payment.PremiumCollectionNotice;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 支付出账成功监听器（policy 域 Kafka 入站适配器 / 防腐层）
 * <p>
 * 防腐消费 {@code payment-order-paid} 主题：出单收费编排建单后保单停在未生效态，
 * 本监听器把支付成功事实转成 {@link PremiumCollectionNotice}，委托应用层
 * {@link PremiumCollectionResultOrchestrator} 回写实收并驱动生效，补齐此前
 * 「建了支付单却收不到回调、保单永远未生效」的断链。
 * </p>
 * <p>
 * <b>归属 infrastructure（driving adapter）</b>：{@code @KafkaListener} 只做消息接入与防腐翻译，
 * 发命令的编排逻辑下沉 application（{@link PremiumCollectionResultOrchestrator}），
 * infrastructure 不持有 CommandGateway（ArchUnit 固化）。
 * </p>
 * <p>
 * 🔴 <b>必须按 {@code businessType} 过滤本域消息</b>：该主题是支付域对**所有**业务域出账成功的
 * 统一出口（理赔赔付、保全退费同样发布到此），不过滤则赔付金额会被当作保费收讫写回保单。
 * </p>
 * <p>
 * <b>吞异常不重抛（与理赔域 PaymentResultConsumer 的重抛策略有意不同）</b>：本链路的重试不可能
 * 成功的场景占多数——重复投递（实收已记账，聚合按 paymentId 抛重复异常）、保单不存在、
 * 报文无法解析，重抛只会让同一条消息被无限重放而结果不变。故此处记录并返回，
 * ⚠️ 后续应改投死信队列以便人工核查（当前无 DLQ）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentPaidListener {

    /** 支付域出账成功主题（与 payment 域 PaymentConstants.KafkaTopic.PAYMENT_ORDER_PAID 约定一致） */
    private static final String PAYMENT_ORDER_PAID_TOPIC = CrossDomainTopics.PAYMENT_ORDER_PAID;

    /** 保费实收回写编排器（application 层，发命令职责归此，infra 监听器不直接持有 CommandGateway） */
    private final PremiumCollectionResultOrchestrator premiumCollectionResultOrchestrator;

    /** 跨域消息 → 领域值对象装配器（infrastructure 层，防腐翻译不外泄到 application） */
    private final PolicyPaymentResultMapper policyPaymentResultMapper;

    /**
     * 消费支付成功消息，回写保单实收并驱动生效。
     *
     * @param payload 事件 JSON 报文
     */
    @KafkaListener(topics = PAYMENT_ORDER_PAID_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void onPaymentPaid(String payload) {
        log.debug("[保费收讫-入站] 收到支付出账成功消息: {}", payload);
        try {
            PaymentOrderPaidMessage message = JSONObject.parseObject(payload, PaymentOrderPaidMessage.class);
            if (message == null || message.businessId() == null || message.paymentNo() == null
                    || message.amount() == null) {
                log.warn("[保费收讫-入站] 消息字段缺失，忽略: {}", payload);
                return;
            }
            if (!BusinessDomainType.POLICY.getCode().equals(message.businessType())) {
                log.debug("[保费收讫-入站] 非本域支付消息，跳过: businessType={}, businessId={}", message.businessType(),
                        message.businessId());
                return;
            }

            PremiumCollectionNotice notice = policyPaymentResultMapper.toNotice(message);
            log.info("[保费收讫-入站] 回写保费实收: policyId={}, paymentNo={}, 金额={}", notice.policyId(),
                    notice.paymentNo(), notice.collectedAmount());
            premiumCollectionResultOrchestrator.onPaymentSucceeded(notice);

        } catch (Exception e) {
            // 重复投递（实收已记账）与保单不存在等场景重试不可能成功，记录但不阻塞消费，避免 Kafka 无限重试。
            // ⚠️ 无 DLQ，失败仅留痕（TODO：集成死信队列）
            log.error("[保费收讫-入站] 实收回写失败: payload={}, 原因={}", payload, e.getMessage(), e);
        }
    }
}
