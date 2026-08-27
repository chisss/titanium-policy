package com.titanium.policy.bootstrap.upcaster;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.axonframework.serialization.SerializedType;
import org.axonframework.serialization.SimpleSerializedType;
import org.axonframework.serialization.upcasting.event.IntermediateEventRepresentation;
import org.axonframework.serialization.upcasting.event.SingleEventUpcaster;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.titanium.policy.event.insurance.InsuranceCreatedEvent;

/**
 * 将无版本的投保单创建事件升级到结构化险种段版本。
 * <p>
 * 历史事件只保存 {@code productCodes}，无法还原产品ID、名称、保额等未知信息。本转换仅补充能够从旧载荷
 * 确定的段序号、主附险关系和产品编码，避免为历史数据伪造业务事实。
 * </p>
 */
@Component
public class InsuranceCreatedEventUpcaster extends SingleEventUpcaster {

    static final String TARGET_REVISION = "2";

    private static final String EVENT_TYPE = InsuranceCreatedEvent.class.getName();
    private static final SerializedType TARGET_TYPE = new SimpleSerializedType(EVENT_TYPE, TARGET_REVISION);

    @Override
    protected boolean canUpcast(IntermediateEventRepresentation representation) {
        SerializedType type = representation.getType();
        return EVENT_TYPE.equals(type.getName()) && type.getRevision() == null;
    }

    @Override
    protected IntermediateEventRepresentation doUpcast(IntermediateEventRepresentation representation) {
        return representation.upcastPayload(TARGET_TYPE, JsonNode.class, this::upcastPayload);
    }

    private JsonNode upcastPayload(JsonNode payload) {
        if (!(payload instanceof ObjectNode objectNode) || objectNode.has("insuranceLines")) {
            return payload;
        }

        ObjectNode upgraded = objectNode.deepCopy();
        JsonNode productCodes = upgraded.remove("productCodes");
        upgraded.set("insuranceLines", toInsuranceLines(upgraded.path("insuranceId").asText(), productCodes));
        return upgraded;
    }

    private ArrayNode toInsuranceLines(String insuranceId, JsonNode productCodes) {
        ArrayNode lines = JsonNodeFactory.instance.arrayNode();
        if (productCodes == null || !productCodes.isArray()) {
            return lines;
        }

        String mainLineId = null;
        for (int index = 0; index < productCodes.size(); index++) {
            int lineNo = index + 1;
            String lineId = deterministicLineId(insuranceId, lineNo);
            if (mainLineId == null) {
                mainLineId = lineId;
            }

            ObjectNode line = lines.addObject();
            line.put("lineId", lineId);
            line.put("lineNo", lineNo);
            line.put("productCategory", lineNo == 1 ? "MAIN" : "RIDER");
            if (lineNo > 1) {
                line.put("parentLineId", mainLineId);
            }
            line.putNull("productId");
            line.set("productCode", productCodes.get(index));
            line.put("lineStatus", "UNDERWRITING");
        }
        return lines;
    }

    private String deterministicLineId(String insuranceId, int lineNo) {
        String seed = (insuranceId == null ? "" : insuranceId) + lineNo;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
