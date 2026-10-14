package com.titanium.policy.infrastructure.messaging;

import java.time.LocalDateTime;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSONObject;

import com.titanium.common.context.RequestContextHolder;
import com.titanium.common.messaging.Idempotent;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.metadata.topic.CrossDomainTopics;
import com.titanium.policy.application.command.insurance.InsuranceApplicationService;
import com.titanium.policy.infrastructure.messaging.inbound.UnderwritingDecidedMessage;
import com.titanium.policy.valueobject.insurance.UnderwritingResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 核保决策事件监听器（policy 域 Kafka 入站适配器 / 防腐层）——核保结论异步回流轨
 * <p>
 * 监听核保域发布的"核保决策完成"事件，解析为 policy 域 {@link UnderwritingResult} 后委托应用层
 * 回写投保单聚合，与同步轨共同构成核保结论的「双轨回流」。
 * </p>
 * <p>
 * 🔴 <b>两轨并非按场景分界（原注释「以是否需人工介入为天然分界」已订正）</b>：核保域
 * {@code UnderwritingKafkaEventPublisher} 对每个 {@code UnderwritingDecidedEvent}
 * <b>无条件</b>发布，而同步轨（{@code UnderwritingDecisionGateway}）在自动决策件上是即时返回的，
 * 故<b>同一决策两轨都会到达本域</b>。去重不在此处、也不靠场景划分，而由投保单聚合承担——
 * {@code Insurance.isRedeliveredResult}（同核保单号 + 同结论 ⇒ 静默幂等、不发事件、不改状态），
 * 两轨最终收敛到同一 {@code ReceiveUnderwritingResultCommand}。守卫测试见
 * {@code InsuranceUnderwritingResultIdempotencyTest}（含跨轨形态用例）与本类用例。
 * </p>
 * <p>
 * <b>归属 infrastructure（driving adapter）</b>：{@code @KafkaListener} 消费外部消息后调用应用层用例，
 * 依赖方向 infra→application 符合洋葱架构。<b>防腐设计</b>：以原始 JSON 解析，不依赖核保域任何类。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnderwritingDecidedEventListener {

    /** 核保决策事件主题（与核保域 UnderwritingConstants.TOPIC_UNDERWRITING_DECIDED 约定一致） */
    private static final String UNDERWRITING_DECIDED_TOPIC = CrossDomainTopics.UNDERWRITING_DECIDED;

    private final InsuranceApplicationService insuranceApplicationService;

    /**
     * 消费核保决策事件，翻译为核保结果后回写投保单聚合。
     *
     * @param payload 事件 JSON 报文
     */
    @Idempotent(strategy = Idempotent.Strategy.AGGREGATE_GUARD,
            reason = "重复投递的核保结论由聚合静默吸收（Insurance.isRedeliveredResult：同核保单号+同结论不发事件），同步/异步两轨收敛到同一命令")
    @KafkaListener(topics = UNDERWRITING_DECIDED_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void onUnderwritingDecided(String payload) {
        // 一次性反序列化为防腐入站消息（不依赖核保域类型），取代手工逐字段解析与嵌套值对象提取
        UnderwritingDecidedMessage message = JSONObject.parseObject(payload, UnderwritingDecidedMessage.class);
        // g02-04：投保单号双读——新字段 insuranceId 优先，存量事件缺失时回退 policyId（详见消息类 javadoc）
        String insuranceId = message.insuranceIdOrPolicyIdValue();
        String underwritingId = message.underwritingIdValue();
        if (insuranceId == null) {
            log.warn("[核保回流] 事件缺少投保单号（insuranceId 与 policyId 均为空），忽略: underwritingId={}",
                    underwritingId);
            return;
        }
        String tenantId = message.tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            log.error("[核保回流] 事件缺少租户，已跳过: insuranceId={}, underwritingId={}",
                    insuranceId, underwritingId);
            return;
        }

        ConclusionType resultCode = parseConclusion(message.conclusionType());
        UnderwritingResult result = new UnderwritingResult(underwritingId, resultCode, message.decidedBy(),
                message.decidedBy(), LocalDateTime.now(), null, message.extraPremiumRatioValue());

        log.info("[核保回流] 异步回写核保结论: insuranceId={}, underwritingId={}, 结论={}", insuranceId,
                underwritingId, resultCode);
        // 本方法运行在 Kafka 监听线程（无 Web 请求上下文）。核保结论回流链路的终点是出单 Saga，
        // 该 Saga 会经 product/billing/payment 等域的 Feign 客户端出站（路径均 /api/v1/** 强制准入，
        // 缺租户头即 400）。故须在**派发命令之前**按消息载荷建立身份作用域：Axon 的消息分发/处理
        // 拦截器据此把身份带到下游事件线程，出站 Feign 才能自动补齐 X-Tenant-Id。
        // 作用域退出即还原，不污染线程池复用的消费线程。
        RequestContextHolder.runAsTenant(tenantId,
                () -> insuranceApplicationService.receiveUnderwritingResult(insuranceId, result, tenantId));
    }

    /**
     * 核保结论 code → 枚举，非法/缺失时按延期处理，避免异常逃逸阻断消费。
     * <p>
     * 🔴 <b>此处窄捕获 {@link IllegalArgumentException} 属刻意例外，不适用 G16/AC-03「失败一律重抛」</b>：
     * ① 只包住 {@code valueOf} 一行纯转换——失败时无任何已发生的副作用需回滚或重试；
     * ② 降级方向保守且显式——未知结论按 {@code POSTPONE}（延期）处置并落 warn，既不放行出单也不丢结论；
     * ③ 重抛只会把一条可处置的消息变成死信、结论本身反被丢弃。
     * AC-03 的判据面是「全局 {@code catch (Exception)} 吞没流程失败」，本处捕获的是具体异常且有降级语义。
     * 消费链路的其余异常不在 try 内，照常逃逸至 Kafka 容器的有界重试 → 死信主题。
     * </p>
     */
    private ConclusionType parseConclusion(String code) {
        if (code == null) {
            return ConclusionType.POSTPONE;
        }
        try {
            return ConclusionType.valueOf(code);
        } catch (IllegalArgumentException ex) {
            log.warn("[核保回流] 未知核保结论 code={}，按延期处理", code);
            return ConclusionType.POSTPONE;
        }
    }
}
