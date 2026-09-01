package com.titanium.policy.application.orchestration.issuance.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.titanium.common.exception.BusinessException;
import com.titanium.metadata.enums.policy.PolicyLineStatus;
import com.titanium.metadata.enums.product.ProductEnum.PaymentFrequency;
import com.titanium.metadata.enums.product.ProductEnum.PeriodUnit;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.entity.insurance.InsuranceLine;
import com.titanium.policy.entity.policy.InsuredSubject;
import com.titanium.policy.port.PremiumCalculationGateway;
import com.titanium.policy.valueobject.policy.LineCoveragePeriod;
import com.titanium.policy.valueobject.policy.LinePaymentTerms;

class InsuranceLinePremiumResolverTest {

    private static final String TENANT_ID = "TENANT_001";

    @Test
    void resolvesEveryMissingLineWithoutOverwritingExistingPremium() {
        RecordingGateway gateway = new RecordingGateway(Map.of(
                "PRODUCT_MAIN", new BigDecimal("800.00"),
                "PRODUCT_RIDER", new BigDecimal("200.00")));
        InsuranceLinePremiumResolver resolver = new InsuranceLinePremiumResolver(gateway);
        InsuranceLine main = line("LINE_001", 1, ProductCategory.MAIN, null, "PRODUCT_MAIN", null);
        InsuranceLine rider = line("LINE_002", 2, ProductCategory.RIDER, "LINE_001", "PRODUCT_RIDER", null);

        List<InsuranceLine> resolved = resolver.resolve(List.of(main, rider), TENANT_ID, null);

        assertEquals(Money.of(new BigDecimal("800.00"), "CNY"), resolved.get(0).premium());
        assertEquals(Money.of(new BigDecimal("200.00"), "CNY"), resolved.get(1).premium());
        assertEquals(List.of("PRODUCT_MAIN", "PRODUCT_RIDER"), gateway.requestedProductIds);
        assertEquals(35, gateway.requests.get(0).subjectData().get("age"));
        assertEquals("FEMALE", gateway.requests.get(0).subjectData().get("gender"));
        assertEquals(20, gateway.requests.get(0).totalPeriods());
        assertEquals(20, gateway.requests.get(0).coverageYears());

        Money confirmed = Money.of(new BigDecimal("999.00"), "CNY");
        InsuranceLine confirmedLine = main.withPremium(confirmed);
        List<InsuranceLine> preserved = resolver.resolve(List.of(confirmedLine), TENANT_ID,
                Money.of(new BigDecimal("1000.00"), "CNY"));
        assertSame(confirmedLine, preserved.get(0));
        assertEquals(2, gateway.requests.size());
    }

    @Test
    void singleLineUsesWholePolicyPremiumWithoutDuplicateBillingCall() {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        InsuranceLinePremiumResolver resolver = new InsuranceLinePremiumResolver(request -> {
            calls.incrementAndGet();
            throw new IllegalStateException("billing unavailable");
        });
        Money fallback = Money.of(new BigDecimal("1000.00"), "CNY");

        List<InsuranceLine> resolved = resolver.resolve(
                List.of(line("LINE_001", 1, ProductCategory.MAIN, null, "PRODUCT_MAIN", null)), TENANT_ID,
                fallback);

        assertEquals(fallback, resolved.get(0).premium());
        assertEquals(0, calls.get());
    }

    @Test
    void multiLinePricingFailureIsExplicit() {
        InsuranceLinePremiumResolver resolver = new InsuranceLinePremiumResolver(request -> {
            if ("PRODUCT_RIDER".equals(request.productId())) {
                throw new IllegalStateException("rider rate missing");
            }
            return new PremiumCalculationGateway.StandardPremiumResult(new BigDecimal("800.00"),
                    new BigDecimal("800.00"), 1, "CNY");
        });
        List<InsuranceLine> lines = List.of(
                line("LINE_001", 1, ProductCategory.MAIN, null, "PRODUCT_MAIN", null),
                line("LINE_002", 2, ProductCategory.RIDER, "LINE_001", "PRODUCT_RIDER", null));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> resolver.resolve(lines, TENANT_ID, Money.of(new BigDecimal("1000.00"), "CNY")));

        assertEquals("险种段保费计算失败: lineNo=2, productId=PRODUCT_RIDER", exception.getMessage());
        assertEquals("ISSUANCE_LINE_PREMIUM_CALCULATION_FAILED", exception.getErrorCode());
    }

    private InsuranceLine line(String lineId, int lineNo, ProductCategory category, String parentLineId,
                               String productId, Money premium) {
        LocalDateTime start = LocalDateTime.of(2026, 8, 13, 0, 0);
        InsuredSubject subject = InsuredSubject.ofPerson("SUBJECT_001", "CUSTOMER_001", "李四",
                Money.of(new BigDecimal("500000"), "CNY"), Map.of("age", 35, "gender", "FEMALE"));
        return new InsuranceLine(lineId, lineNo, category, parentLineId, productId, productId, "测试产品", null,
                Money.of(new BigDecimal("500000"), "CNY"), premium,
                LineCoveragePeriod.fixedTerm(start, start.plusYears(20), 20, PeriodUnit.YEAR),
                new LinePaymentTerms(PaymentFrequency.ANNUAL, 20), List.of(subject), null, null,
                PolicyLineStatus.UNDERWRITING);
    }

    private static class RecordingGateway implements PremiumCalculationGateway {
        private final Map<String, BigDecimal> premiumByProduct;
        private final java.util.ArrayList<String> requestedProductIds = new java.util.ArrayList<>();
        private final java.util.ArrayList<StandardPremiumRequest> requests = new java.util.ArrayList<>();

        RecordingGateway(Map<String, BigDecimal> premiumByProduct) {
            this.premiumByProduct = premiumByProduct;
        }

        @Override
        public StandardPremiumResult calculatePremium(StandardPremiumRequest request) {
            requestedProductIds.add(request.productId());
            requests.add(request);
            BigDecimal premium = premiumByProduct.get(request.productId());
            return new StandardPremiumResult(premium, premium, request.totalPeriods(), request.currency());
        }
    }
}
