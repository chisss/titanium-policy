package com.titanium.policy.application.orchestration.issuance.assembler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.policy.PolicyLineStatus;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.entity.insurance.InsuranceLine;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.port.ClauseServicePort;
import com.titanium.policy.port.ProductServicePort;
import com.titanium.policy.valueobject.pricing.PremiumCalculationReference;
import com.titanium.policy.valueobject.product.ProductBasicInfo;

class PolicyProductAssemblerTest {

    @Test
    void mapsRiderParentToGeneratedPolicyProductIdAndConservesPremium() {
        ProductServicePort productServicePort = mock(ProductServicePort.class);
        ClauseServicePort clauseServicePort = mock(ClauseServicePort.class);
        when(productServicePort.getClauseRefs(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of());
        when(productServicePort.getProductBasicInfo("PRODUCT_1", "TENANT_001"))
                .thenReturn(new ProductBasicInfo("PRODUCT_1", "P001", "产品域主险名", "V1.0", null,
                        "EFFECTIVE"));
        when(productServicePort.getProductBasicInfo("PRODUCT_2", "TENANT_001"))
                .thenReturn(new ProductBasicInfo("PRODUCT_2", "P002", "产品域附加险名", "V2.0", null,
                        "EFFECTIVE"));
        PolicyProductAssembler assembler = new PolicyProductAssembler(productServicePort, clauseServicePort,
                new InsuranceLineAssembler(productServicePort));
        InsuranceLine main = line("INSURANCE_LINE_MAIN", 1, ProductCategory.MAIN, null, "投保段主险名", "800.00");
        InsuranceLine rider = line("INSURANCE_LINE_RIDER", 2, ProductCategory.RIDER, "INSURANCE_LINE_MAIN",
                null, "200.00");

        List<PolicyProduct> products = assembler.assembleFromInsuranceLines(
                List.of(main, rider), "TENANT_001",
                List.of(reference(main, "PLAN_MAIN"), reference(rider, "PLAN_RIDER")));

        PolicyProduct mainProduct = products.get(0);
        PolicyProduct riderProduct = products.get(1);
        assertNull(mainProduct.parentPolicyProductId());
        assertEquals(mainProduct.policyProductId(), riderProduct.parentPolicyProductId());
        assertNotEquals(main.lineId(), riderProduct.parentPolicyProductId());
        assertEquals("投保段主险名", mainProduct.productName());
        assertEquals("V1.0", mainProduct.productVersion());
        assertEquals("PLAN_MAIN", mainProduct.pricingPlanVersion());
        assertEquals("产品域附加险名", riderProduct.productName());
        assertEquals("V2.0", riderProduct.productVersion());
        assertEquals("PLAN_RIDER", riderProduct.pricingPlanVersion());
        assertEquals(Money.of(new BigDecimal("1000.00"), "CNY"), assembler.sumPremium(products));
        verify(productServicePort).getProductBasicInfo("PRODUCT_1", "TENANT_001");
        verify(productServicePort).getProductBasicInfo("PRODUCT_2", "TENANT_001");
    }

    @Test
    void keepsInsuranceLineNameWhenProductBasicInfoIsUnavailable() {
        ProductServicePort productServicePort = mock(ProductServicePort.class);
        ClauseServicePort clauseServicePort = mock(ClauseServicePort.class);
        when(productServicePort.getClauseRefs("PRODUCT_1", "TENANT_001")).thenReturn(List.of());
        PolicyProductAssembler assembler = new PolicyProductAssembler(productServicePort, clauseServicePort,
                new InsuranceLineAssembler(productServicePort));

        PolicyProduct product = assembler.assembleFromInsuranceLines(
                List.of(lineWithoutVersion(
                        "INSURANCE_LINE_MAIN", 1, ProductCategory.MAIN, null, "投保段主险名", "800.00")),
                "TENANT_001").get(0);

        assertEquals("投保段主险名", product.productName());
        assertNull(product.productVersion());
        verify(productServicePort).getProductBasicInfo("PRODUCT_1", "TENANT_001");
    }

    private InsuranceLine line(String lineId, int lineNo, ProductCategory category, String parentLineId,
                               String productName, String premium) {
        return new InsuranceLine(lineId, lineNo, category, parentLineId, "PRODUCT_" + lineNo,
                "P00" + lineNo, productName, "V" + lineNo + ".0", null,
                Money.of(new BigDecimal("500000"), "CNY"),
                Money.of(new BigDecimal(premium), "CNY"), null, null, List.of(), null, null,
                PolicyLineStatus.UNDERWRITING);
    }

    private InsuranceLine lineWithoutVersion(String lineId, int lineNo, ProductCategory category, String parentLineId,
                                             String productName, String premium) {
        return new InsuranceLine(lineId, lineNo, category, parentLineId, "PRODUCT_" + lineNo,
                "P00" + lineNo, productName, null, Money.of(new BigDecimal("500000"), "CNY"),
                Money.of(new BigDecimal(premium), "CNY"), null, null, List.of(), null, null,
                PolicyLineStatus.UNDERWRITING);
    }

    private PremiumCalculationReference reference(InsuranceLine line, String planVersion) {
        return new PremiumCalculationReference(
                "calculation-" + line.lineId(), "hash", line.productId(), line.productVersion(), planVersion,
                BigDecimal.ONE, "CNY", line.lineId());
    }
}
