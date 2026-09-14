package com.titanium.policy.infrastructure.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSONObject;

import com.titanium.metadata.enums.BusinessDomainType;
import com.titanium.metadata.topic.CrossDomainTopics;
import com.titanium.policy.infrastructure.messaging.inbound.PaymentOrderFailedMessage;

import lombok.extern.slf4j.Slf4j;

/**
 * 支付出账未成功监听器（policy 域 Kafka 入站适配器 / 防腐层）
 * <p>
 * 防腐消费 {@code payment-order-failed} 主题：出单收费编排建单后保单停在待生效态，
 * 收讫成功由 {@link PaymentPaidListener} 驱动生效；本监听器是其**失败侧对应物**——支付未成功时，
 * 保单本该停在待生效（钱没到，不生效是对的），但此前该事实在保单域**完全无感**：全仓该主题的
 * 消费者只有 claim 域，policy 侧零监听器，故「哪张保单因支付失败卡在待生效」无从检索。
 * </p>
 * <p>
 * 🔴 <b>本监听器有意不改动任何状态</b>：保单停在待生效态即正确的领域状态，无需额外命令；
 * 补偿由两条既有通道承担——保费实际到账时由 {@link PaymentPaidListener} 与
 * {@code PolicyActivationScheduler}（待生效 + 起期已到）驱动生效；长期未收讫则由 billing 逾期
 * 失效链路处理。本类的职责是**可观测性**：把「出款未成功」这一跨域事实落成保单域的结构化日志，
 * 作为该链路的唯一观测点供监控与人工跟进。
 * </p>
 * <p>
 * 🔴 <b>必须按 {@code businessType} 过滤本域消息</b>：该主题是支付域对**所有**业务域出款未成功的
 * 统一出口（理赔赔付、保全退费同样发布到此），不过滤会把赔付失败误报为保费收取失败。
 * </p>
 * <p>
 * <b>吞异常不重抛</b>（与 {@link PaymentPaidListener} 同策略）：报文无法解析、字段缺失等场景重抛
 * 只会让同一条消息被无限重放而结果不变。⚠️ 与同域其他入站链路一致，当前无 DLQ。
 * </p>
 */
@Slf4j
@Component
public class PremiumCollectionFailedListener {

    /** 支付域出账未成功主题（与 payment 域 PaymentConstants.KafkaTopic.PAYMENT_ORDER_FAILED 约定一致） */
    private static final String PAYMENT_ORDER_FAILED_TOPIC = CrossDomainTopics.PAYMENT_ORDER_FAILED;

    /** 结果类型「渠道确认失败」（对端 {@code PaymentOrderStatus.FAILED} 的 code） */
    private static final String RESULT_TYPE_FAILED = "FAILED";

    /**
     * 消费支付未成功消息，记录保费收取失败事实供人工跟进。
     *
     * @param payload 事件 JSON 报文
     */
    @KafkaListener(topics = PAYMENT_ORDER_FAILED_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void onPaymentFailed(String payload) {
        log.debug("[保费收取失败-入站] 收到支付出账未成功消息: {}", payload);
        try {
            PaymentOrderFailedMessage message = JSONObject.parseObject(payload, PaymentOrderFailedMessage.class);
            if (message == null || message.businessId() == null || message.paymentNo() == null) {
                log.warn("[保费收取失败-入站] 消息字段缺失，忽略: {}", payload);
                return;
            }
            if (!BusinessDomainType.POLICY.getCode().equals(message.businessType())) {
                log.debug("[保费收取失败-入站] 非本域支付消息，跳过: businessType={}, businessId={}", message.businessType(),
                        message.businessId());
                return;
            }

            if (RESULT_TYPE_FAILED.equals(message.resultType())) {
                log.error("[保费收取失败-入站] 保费支付失败，保单停留待生效，需人工跟进: policyId={}, paymentNo={}, "
                        + "tenantId={}, 发生时间={}, 原因={}", message.businessId(), message.paymentNo(), message.tenantId(),
                        message.occurredAt(), message.reason());
            } else {
                log.warn("[保费收取失败-入站] 支付单被撤销，保单停留待生效: policyId={}, paymentNo={}, tenantId={}, "
                        + "结果类型={}, 原因={}", message.businessId(), message.paymentNo(), message.tenantId(),
                        message.resultType(), message.reason());
            }
        } catch (Exception e) {
            // 报文无法解析等场景重试不可能成功，记录但不阻塞消费，避免 Kafka 无限重试。
            // ⚠️ 无 DLQ，失败仅留痕（与同域其他入站链路一致）
            log.error("[保费收取失败-入站] 消息处理失败: payload={}, 原因={}", payload, e.getMessage(), e);
        }
    }
}
