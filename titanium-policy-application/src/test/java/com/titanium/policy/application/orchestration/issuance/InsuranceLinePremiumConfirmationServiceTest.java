package com.titanium.policy.application.orchestration.issuance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.titanium.common.exception.BusinessException;
import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;
import com.titanium.metadata.enums.customer.CustomerEnum.IdCardType;
import com.titanium.metadata.enums.policy.PolicyLineStatus;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.application.orchestration.issuance.assembler.ConfirmedPremiumRequestAssembler;
import com.titanium.policy.entity.insurance.InsuranceLine;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.entity.policy.InsuredSubject;
import com.titanium.policy.port.ConfirmedPremiumPricingPort;
import com.titanium.policy.valueobject.policy.LineCoveragePeriod;
import com.titanium.policy.valueobject.policy.LinePaymentTerms;
import com.titanium.policy.valueobject.pricing.ConfirmedPremiumRequest;
import com.titanium.policy.valueobject.pricing.ConfirmedPremiumResult;

class InsuranceLinePremiumConfirmationServiceTest {

    private static final LocalDateTime BUSINESS_TIME = LocalDateTime.of(2026, 9, 1, 0, 0);

    @Test
    void underwritingSurchargeUsesProductConfirmedTotalWithoutLocalRecalculation() {
        RecordingPort port = new RecordingPort();
        port.result = new ConfirmedPremiumResult(
                "CALC-1", "CONFIRMED", "ISSUANCE_CONFIRM", "PRODUCT-1", "V3", "CNY",
                new BigDecimal("1000.00"), new BigDecimal("1123.45"), "PP-7", "HASH-1");
        InsuranceLinePremiumConfirmationService service = new InsuranceLinePremiumConfirmationService(port, new ConfirmedPremiumRequestValidator(),
                new ConfirmedPremiumRequestAssembler());

        InsuranceLinePremiumConfirmationService.ConfirmationSummary summary = service.confirm(
                List.of(line(new BigDecimal("0.12345"))), null, "INSURANCE-1", "BIZ-1",
                BUSINESS_TIME, "TENANT-1", "CHANNEL-1", 1, true);

        assertEquals(new BigDecimal("1000.00"), summary.standardPremium().value());
        assertEquals(new BigDecimal("1123.45"), summary.totalPremium().value());
        assertEquals(new BigDecimal("1123.45"), summary.lines().get(0).payablePremium().value());
        assertEquals(1, summary.calculationReferences().size());
        assertEquals("ISSUANCE-aa74cd00-bf67-3ba4-8dc6-d0f45a8a4a3b",
                port.requests.get(0).calculationRequestId());
        assertEquals("SURCHARGE_RATE", port.requests.get(0).underwritingAdjustments().get(0).type());
        assertEquals(new BigDecimal("0.12345"), port.requests.get(0).underwritingAdjustments().get(0).value());
        assertEquals("CHANNEL-1", port.requests.get(0).requestSnapshot().channelId());
        assertEquals(1, port.requests.get(0).requestSnapshot().policyYear());
    }

    @Test
    void productConfirmationFailureStopsIssuanceWithoutUsingQuotedPremium() {
        ConfirmedPremiumPricingPort failingPort = request -> {
            throw new IllegalStateException("pricing unavailable");
        };
        InsuranceLinePremiumConfirmationService service = new InsuranceLinePremiumConfirmationService(failingPort,
                new ConfirmedPremiumRequestValidator(),
                new ConfirmedPremiumRequestAssembler());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.confirm(
                List.of(line(null).withPremium(Money.of(new BigDecimal("999.00"), "CNY"))), null,
                "INSURANCE-1", "BIZ-1", BUSINESS_TIME, "TENANT-1", false));

        assertEquals(PolicyErrorCode.ISSUANCE_PREMIUM_CONFIRMATION_FAILED.getCode(), exception.getErrorCode());
    }

    @Test
    void longIssuanceReferenceProducesDatabaseSafeIdempotencyKey() {
        RecordingPort port = new RecordingPort();
        port.result = new ConfirmedPremiumResult(
                "CALC-1", "CONFIRMED", "ISSUANCE_CONFIRM", "PRODUCT-1", "V3", "CNY",
                new BigDecimal("1000.00"), new BigDecimal("1000.00"), "PP-7", "HASH-1");
        InsuranceLinePremiumConfirmationService service = new InsuranceLinePremiumConfirmationService(port, new ConfirmedPremiumRequestValidator(),
                new ConfirmedPremiumRequestAssembler());

        service.confirm(List.of(line(null)), null, "x".repeat(200), "BIZ-1", BUSINESS_TIME,
                "TENANT-1", false);

        assertTrue(port.requests.get(0).calculationRequestId().length() <= 64);
    }

    @Test
    void propertyLineUsesInsuredPartyPricingProfileWithoutCreatingPersonSubject() {
        RecordingPort port = new RecordingPort();
        port.result = new ConfirmedPremiumResult(
                "CALC-1", "CONFIRMED", "ISSUANCE_CONFIRM", "PRODUCT-1", "V3", "CNY",
                new BigDecimal("1000.00"), new BigDecimal("1000.00"), "PP-7", "HASH-1");
        InsuranceLinePremiumConfirmationService service = new InsuranceLinePremiumConfirmationService(port, new ConfirmedPremiumRequestValidator(),
                new ConfirmedPremiumRequestAssembler());
        InsuranceLine propertyLine = line(null);
        propertyLine = new InsuranceLine(
                propertyLine.lineId(), propertyLine.lineNo(), propertyLine.productCategory(),
                propertyLine.parentLineId(), propertyLine.productId(), propertyLine.productCode(),
                propertyLine.productName(), propertyLine.productVersion(), propertyLine.insuranceType(),
                propertyLine.sumInsured(), propertyLine.premium(), propertyLine.coveragePeriod(),
                propertyLine.paymentTerms(), List.of(), propertyLine.underwritingConclusion(),
                propertyLine.extraPremiumRatio(), propertyLine.lineStatus());
        InsuredPartyList parties = new InsuredPartyList("LIST-1", null,
                List.of(new InsuredPartyList.InsuredInfo(
                        "CUSTOMER-1", "INSURED-1", "被保险人", IdCardType.CHINA_ID_CARD,
                        "110101199001011234", 36, CustomerGender.FEMALE, "13800000000", "SELF", null)),
                List.of());

        service.confirm(List.of(propertyLine), parties, "INSURANCE-1", "BIZ-1", BUSINESS_TIME,
                "TENANT-1", false);

        assertEquals(36, port.requests.get(0).age());
        assertEquals("FEMALE", port.requests.get(0).gender());
        assertTrue(propertyLine.insuredSubjects().isEmpty());
    }

    @Test
    void anniversaryMinusOneDayCoverageCountsAsOnePricingYear() {
        RecordingPort port = new RecordingPort();
        port.result = new ConfirmedPremiumResult(
                "CALC-1", "CONFIRMED", "ISSUANCE_CONFIRM", "PRODUCT-1", "V3", "CNY",
                new BigDecimal("1000.00"), new BigDecimal("1000.00"), "PP-7", "HASH-1");
        InsuranceLinePremiumConfirmationService service = new InsuranceLinePremiumConfirmationService(port, new ConfirmedPremiumRequestValidator(),
                new ConfirmedPremiumRequestAssembler());
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        InsuranceLine oneYearLine = new InsuranceLine(
                "LINE-1", 1, ProductCategory.MAIN, null, "PRODUCT-1", "P001", "测试产品", "V3", null,
                Money.of(new BigDecimal("100000"), "CNY"), null,
                LineCoveragePeriod.fixedTerm(start, start.plusYears(1).minusDays(1), null, null),
                LinePaymentTerms.lumpSum(), List.of(InsuredSubject.ofPerson(
                        "SUBJECT-1", "CUSTOMER-1", "被保险人", Money.of(new BigDecimal("100000"), "CNY"),
                        Map.of("age", 35, "gender", "M"))), ConclusionType.ACCEPT, null,
                PolicyLineStatus.ACCEPTED);

        service.confirm(List.of(oneYearLine), null, "INSURANCE-1", "BIZ-1", start, "TENANT-1", false);

        assertEquals(1, port.requests.get(0).coverageTermYears());
    }

    private InsuranceLine line(BigDecimal extraPremiumRatio) {
        InsuredSubject subject = InsuredSubject.ofPerson(
                "SUBJECT-1", "CUSTOMER-1", "被保险人", Money.of(new BigDecimal("100000"), "CNY"),
                Map.of("age", 35, "gender", "M"));
        return new InsuranceLine(
                "LINE-1", 1, ProductCategory.MAIN, null, "PRODUCT-1", "P001", "测试产品", "V3", null,
                Money.of(new BigDecimal("100000"), "CNY"), null,
                LineCoveragePeriod.fixedTerm(BUSINESS_TIME, BUSINESS_TIME.plusYears(20), 20, null),
                LinePaymentTerms.lumpSum(), List.of(subject), ConclusionType.MODIFY, extraPremiumRatio,
                PolicyLineStatus.ACCEPTED);
    }

    private static final class RecordingPort implements ConfirmedPremiumPricingPort {
        private final List<ConfirmedPremiumRequest> requests = new ArrayList<>();
        private ConfirmedPremiumResult result;

        @Override
        public ConfirmedPremiumResult confirm(ConfirmedPremiumRequest request) {
            requests.add(request);
            return result;
        }
    }
}
