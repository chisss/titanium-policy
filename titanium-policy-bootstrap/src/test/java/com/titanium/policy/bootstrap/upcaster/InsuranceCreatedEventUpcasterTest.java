package com.titanium.policy.bootstrap.upcaster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.axonframework.eventhandling.GenericDomainEventEntry;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.upcasting.event.InitialEventRepresentation;
import org.axonframework.serialization.upcasting.event.IntermediateEventRepresentation;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.policy.entity.insurance.InsuranceLine;
import com.titanium.policy.event.insurance.InsuranceCreatedEvent;

class InsuranceCreatedEventUpcasterTest {

    private static final String EVENT_TYPE = InsuranceCreatedEvent.class.getName();

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Serializer serializer = JacksonSerializer.builder().objectMapper(objectMapper).build();
    private final InsuranceCreatedEventUpcaster upcaster = new InsuranceCreatedEventUpcaster();

    @Test
    void upcastsLegacyProductCodesToDeterministicInsuranceLines() {
        String legacyJson = """
                {
                  "insuranceId":"INSURANCE_001",
                  "insuranceNo":"INS_001",
                  "proposalId":"PROPOSAL_001",
                  "policyForm":"INDIVIDUAL",
                  "holderId":"CUSTOMER_001",
                  "insuredCount":1,
                  "exactPremium":1200.00,
                  "insurancePeriodStart":"2026-01-01T00:00:00",
                  "insurancePeriodEnd":"2046-01-01T00:00:00",
                  "productCodes":["MAIN-CODE","RIDER-CODE"],
                  "underwritingPriority":2,
                  "createTime":"2026-01-01T00:00:00",
                  "tenantId":"TENANT_001"
                }
                """;

        IntermediateEventRepresentation result = upcast(legacyJson);
        InsuranceCreatedEvent event = serializer.deserialize(result.getData(byte[].class));

        assertEquals(InsuranceCreatedEventUpcaster.TARGET_REVISION, result.getType().getRevision());
        assertEquals(List.of("MAIN-CODE", "RIDER-CODE"), event.productCodes());
        assertEquals(2, event.insuranceLines().size());

        InsuranceLine main = event.insuranceLines().get(0);
        InsuranceLine rider = event.insuranceLines().get(1);
        assertEquals(deterministicLineId("INSURANCE_001", 1), main.lineId());
        assertEquals(deterministicLineId("INSURANCE_001", 2), rider.lineId());
        assertEquals(ProductCategory.MAIN, main.productCategory());
        assertEquals(ProductCategory.RIDER, rider.productCategory());
        assertNull(main.parentLineId());
        assertEquals(main.lineId(), rider.parentLineId());
        assertNull(main.productId());
        assertNull(rider.productId());
        assertEquals("TENANT_001", event.tenantId());
        assertFalse(result.getData(JsonNode.class).getData().has("productCodes"));
    }

    @Test
    void onlyAddsRevisionWhenTransitionalPayloadAlreadyContainsInsuranceLines() throws Exception {
        String transitionalJson = """
                {
                  "insuranceId":"INSURANCE_TRANSITION",
                  "insuranceNo":"INS_TRANSITION",
                  "insuranceLines":[{
                    "lineId":"EXISTING_LINE",
                    "lineNo":7,
                    "productCategory":"MAIN",
                    "productId":"PRODUCT_001",
                    "productCode":"KEEP-ME"
                  }]
                }
                """;
        JsonNode originalPayload = objectMapper.readTree(transitionalJson);

        IntermediateEventRepresentation result = upcast(transitionalJson);

        assertEquals(InsuranceCreatedEventUpcaster.TARGET_REVISION, result.getType().getRevision());
        assertEquals(originalPayload, result.getData(JsonNode.class).getData());
        InsuranceCreatedEvent event = serializer.deserialize(result.getData(byte[].class));
        assertEquals("EXISTING_LINE", event.insuranceLines().getFirst().lineId());
        assertEquals("PRODUCT_001", event.insuranceLines().getFirst().productId());
    }

    private IntermediateEventRepresentation upcast(String payload) {
        byte[] metadata = "{}".getBytes(StandardCharsets.UTF_8);
        GenericDomainEventEntry<byte[]> entry = new GenericDomainEventEntry<>("Insurance", "INSURANCE_001", 0,
                "EVENT_001", Instant.parse("2026-01-01T00:00:00Z"), EVENT_TYPE, null,
                payload.getBytes(StandardCharsets.UTF_8), metadata);
        InitialEventRepresentation initial = new InitialEventRepresentation(entry, serializer);
        return upcaster.upcast(Stream.of(initial)).findFirst().orElseThrow();
    }

    private String deterministicLineId(String insuranceId, int lineNo) {
        return UUID.nameUUIDFromBytes((insuranceId + lineNo).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
