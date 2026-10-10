package com.titanium.policy.infrastructure.messaging.inbound;

import java.math.BigDecimal;

/**
 * 核保决策事件入站消息（policy 域防腐镜像）
 * <p>
 * 核保域经 Kafka 外发的"核保决策完成"事件的 policy 域防腐镜像。<b>不依赖核保域任何类</b>，
 * 由 fastjson2 一次性反序列化，取代手工 {@code getString} 与嵌套值对象提取。
 * </p>
 * <p>
 * <b>嵌套值对象契约</b>：核保域 {@code PolicyId}/{@code InsuranceId}/{@code UnderwritingId} 均为
 * {@code record(String value)}，经 fastjson2 序列化为 {@code {"value":"..."}}，以内嵌 {@link ValueRef} 精确承接。
 * </p>
 * <p>
 * 🔴 <b>投保单号双读（g02-04）</b>：核保域事件尾部新增语义正确的 {@code insuranceId} 字段，但
 * <b>存量事件不可回放</b>——已落事件流与已发出消息里都没有该字段。故本镜像<b>同时承接两个字段</b>，
 * 取值一律走 {@link #insuranceIdOrPolicyIdValue()}：新字段优先，缺失时回退 {@code policyId}
 * （出单主链路历史上把投保单号装在 {@code policyId} 里）。**不改字段名、不做事件回放**。
 * </p>
 * <p>
 * ⚠️ 回退分支不是纯兼容残留：保全核保路径写入的 {@code policyId} 是**真实保单号**，其核保事件
 * 不带 {@code insuranceId}，回退后拿到的是保单号——这正是该路径消费方需要的主键，两侧语义各自成立。
 * </p>
 */
public record UnderwritingDecidedMessage(ValueRef policyId, ValueRef underwritingId, String conclusionType,
                                         String decidedBy, String tenantId, ExtraPremiumRef extraPremium,
                                         ValueRef insuranceId) {

    /** 核保域 {@code record(String value)} 值对象序列化形态 {@code {"value":"..."}} 的防腐镜像 */
    public record ValueRef(String value) {
    }

    /**
     * 核保域 {@code ExtraPremium} 加费值对象的防腐镜像（UW-3 结构化加费异步回流）。
     * 仅承接 policy 出单所需的加费率字段，其余（固定额/期限/原因）忽略。
     */
    public record ExtraPremiumRef(BigDecimal ratio) {
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
    public BigDecimal extraPremiumRatioValue() {
        return extraPremium != null ? extraPremium.ratio() : null;
    }

    /**
     * 空安全提取投保单号：新字段 {@code insuranceId} 优先，缺失时回退 {@code policyId}
     * <p>
     * 🔴 回退是**必需**的而非可选优化：存量事件无反序列化回填，新字段恒为 null；若只读新字段，
     * 核保结论对存量在途投保单将全部静默丢弃。
     * </p>
     *
     * @return 投保单号；两字段均缺失时返回 null
     */
    public String insuranceIdOrPolicyIdValue() {
        String insurance = insuranceId != null ? insuranceId.value() : null;
        return insurance != null ? insurance : policyIdValue();
    }
}
