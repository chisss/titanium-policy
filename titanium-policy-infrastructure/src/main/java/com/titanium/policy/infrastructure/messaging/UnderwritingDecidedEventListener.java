package com.titanium.policy.infrastructure.messaging;

import java.time.LocalDateTime;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSONObject;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.policy.application.command.InsuranceApplicationService;
import com.titanium.policy.infrastructure.messaging.inbound.UnderwritingDecidedMessage;
import com.titanium.policy.valueobject.insurance.UnderwritingResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 核保决策事件监听器（policy 域 Kafka 入站适配器 / 防腐层）——核保结论异步回流轨
 * <p>
 * 监听核保域发布的"核保决策完成"事件，解析为 policy 域 {@link UnderwritingResult} 后委托应用层
 * 回写投保单聚合，完成人工核保/批量核保场景的「核保结论 → 承保」异步闭环。投保出单主链路仍走
 * 同步 {@code UnderwritingDecisionGateway} 即时拉取，二者以「是否需人工介入」为天然分界，
 * 接收端统一收敛到 {@code receiveUnderwritingResult}，不形成双写重复。
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
    private static final String UNDERWRITING_DECIDED_TOPIC = "underwriting-decided";

    private final InsuranceApplicationService insuranceApplicationService;

    /**
     * 消费核保决策事件，翻译为核保结果后回写投保单聚合。
     *
     * @param payload 事件 JSON 报文
     */
    @KafkaListener(topics = UNDERWRITING_DECIDED_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void onUnderwritingDecided(String payload) {
        // 一次性反序列化为防腐入站消息（不依赖核保域类型），取代手工逐字段解析与嵌套值对象提取
        UnderwritingDecidedMessage message = JSONObject.parseObject(payload, UnderwritingDecidedMessage.class);
        String insuranceId = message.policyIdValue();
        String underwritingId = message.underwritingIdValue();
        if (insuranceId == null) {
            log.warn("[核保回流] 事件缺少 policyId，忽略: underwritingId={}", underwritingId);
            return;
        }

        ConclusionType resultCode = parseConclusion(message.conclusionType());
        UnderwritingResult result = new UnderwritingResult(underwritingId, resultCode, message.decidedBy(),
                message.decidedBy(), LocalDateTime.now(), null, message.extraPremiumRatioValue());

        log.info("[核保回流] 异步回写核保结论: insuranceId={}, underwritingId={}, 结论={}", insuranceId,
                underwritingId, resultCode);
        insuranceApplicationService.receiveUnderwritingResult(insuranceId, result, message.tenantId());
    }

    /** 核保结论 code → 枚举，非法/缺失时按延期处理，避免异常逃逸阻断消费 */
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
