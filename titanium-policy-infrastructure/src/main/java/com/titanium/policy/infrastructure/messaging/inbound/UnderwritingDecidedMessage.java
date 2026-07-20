package com.titanium.policy.infrastructure.messaging.inbound;

/**
 * 核保决策事件入站消息（policy 域防腐镜像）
 * <p>
 * 核保域经 Kafka 外发的"核保决策完成"事件的 policy 域防腐镜像。<b>不依赖核保域任何类</b>，
 * 由 fastjson2 一次性反序列化，取代手工 {@code getString} 与嵌套值对象提取。
 * </p>
 * <p>
 * <b>嵌套值对象契约</b>：核保域 {@code PolicyId}/{@code UnderwritingId} 均为 {@code record(String value)}，
 * 经 fastjson2 序列化为 {@code {"value":"..."}}，以内嵌 {@link ValueRef} 精确承接。
 * </p>
 */
public record UnderwritingDecidedMessage(ValueRef policyId, ValueRef underwritingId, String conclusionType,
                                         String decidedBy, String tenantId, ExtraPremiumRef extraPremium) {

    /** 核保域 {@code record(String value)} 值对象序列化形态 {@code {"value":"..."}} 的防腐镜像 */
    public record ValueRef(String value) {
    }

    /**
     * 核保域 {@code ExtraPremium} 加费值对象的防腐镜像（UW-3 结构化加费异步回流）。
     * 仅承接 policy 出单所需的加费率字段，其余（固定额/期限/原因）忽略。
     */
    public record ExtraPremiumRef(java.math.BigDecimal ratio) {
    }

    /** 空安全提取投保单/保单关联键 */
    public String policyIdValue() {
        return policyId != null ? policyId.value() : null;
    }

    /** 空安全提取核保单号 */
    public String underwritingIdValue() {
        return underwritingId != null ? underwritingId.value() : null;
    }

    /** 空安全提取加费率（无加费时返回 null） */
    public java.math.BigDecimal extraPremiumRatioValue() {
        return extraPremium != null ? extraPremium.ratio() : null;
    }
}
