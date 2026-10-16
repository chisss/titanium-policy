package com.titanium.policy.bootstrap.upcaster;

import java.util.List;

import org.axonframework.serialization.SerializedType;
import org.axonframework.serialization.upcasting.event.IntermediateEventRepresentation;
import org.axonframework.serialization.upcasting.event.SingleEventUpcaster;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.titanium.policy.event.PolicyCreatedEvent;

/**
 * 剥离 {@link PolicyCreatedEvent} 历史载荷中 {@code policyPeriod} 的已删除字段。
 * <p>
 * <b>背景</b>：G11/g11-04（D2 裁决）将等待期/犹豫期自保单级 {@code PolicyPeriod} 下沉至段级
 * {@code LineCoveragePeriod}，并自 {@code PolicyPeriod} record 移除下列 4 个组件。存量事件
 * （该变更前写入）的 {@code policyPeriod} 仍携带这些键，严格模式下反序列化抛
 * {@code UnrecognizedPropertyException}（m26-05 全量回放实测：800 条中 83 条命中，全部为
 * {@code PolicyCreatedEvent}）。当时以全局 lenient 掩盖，本 Upcaster 改为<b>显式剥离</b>承接
 * （规约 R4：不靠容忍承担兼容）。
 * </p>
 * <p>
 * <b>语义无损</b>：lenient 时期这些键本就被静默丢弃（读不到）；剥离只是把「静默丢弃」变为
 * 「显式声明丢弃」。段级等待期取值对存量事件本就缺失，由 {@code LineCoveragePeriod} 基本类型
 * 默认值（0 / null）补位，与现状一致。
 * </p>
 * <p>
 * 🔴 <b>剥离限定在 {@code policyPeriod} 对象内，禁止全局按名删除</b>：这 4 个键名在段级
 * {@code LineCoveragePeriod} 与产品规则 {@code ProductIssueRules} 中仍是<b>合法组件</b>，
 * 全局删会破坏段级/规则数据。同理，本类不做「动态剥离未知键」——那是隐式容忍的变体，
 * 新增漂移点必须在此显式登记（每键注明来源）。
 * </p>
 * <p>
 * 幂等：干净载荷（无任一旧键）原样返回；类型与 revision 不变（本 Upcaster 不改结构、不递增版本）。
 * </p>
 */
@Component
public class PolicyCreatedEventLegacyKeyStripUpcaster extends SingleEventUpcaster {

    private static final String EVENT_TYPE = PolicyCreatedEvent.class.getName();

    /** 承载旧字段的嵌套对象名（保单级期间；段级同名字段在 {@code coveragePeriod} 下，不受影响）。 */
    private static final String POLICY_PERIOD_NODE = "policyPeriod";

    /**
     * {@code policyPeriod} 中已删除的字段名（G11/g11-04 下沉 {@code LineCoveragePeriod} 时移除）。
     */
    private static final List<String> LEGACY_POLICY_PERIOD_FIELDS = List.of(
            "waitingPeriodDays",
            "waitingPeriodEndDate",
            "hesitationPeriodDays",
            "hesitationPeriodEndDate");

    @Override
    protected boolean canUpcast(IntermediateEventRepresentation representation) {
        SerializedType type = representation.getType();
        // 规约 R3：无版本存量（NULL）与显式 v1（"1"）视为同一初始版本
        return EVENT_TYPE.equals(type.getName())
                && (type.getRevision() == null || "1".equals(type.getRevision()));
    }

    @Override
    protected IntermediateEventRepresentation doUpcast(IntermediateEventRepresentation representation) {
        // 类型不变（revision 保持原值）：本 Upcaster 只清洗载荷，不改变事件版本语义
        return representation.upcastPayload(representation.getType(), JsonNode.class, this::stripLegacyFields);
    }

    private JsonNode stripLegacyFields(JsonNode payload) {
        if (!(payload instanceof ObjectNode root) || !(root.get(POLICY_PERIOD_NODE) instanceof ObjectNode period)) {
            return payload;
        }
        if (LEGACY_POLICY_PERIOD_FIELDS.stream().noneMatch(period::has)) {
            return payload;
        }

        ObjectNode stripped = root.deepCopy();
        ((ObjectNode) stripped.get(POLICY_PERIOD_NODE)).remove(LEGACY_POLICY_PERIOD_FIELDS);
        return stripped;
    }
}
