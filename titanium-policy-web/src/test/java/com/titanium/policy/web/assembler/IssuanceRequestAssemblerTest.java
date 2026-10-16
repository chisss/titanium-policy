package com.titanium.policy.web.assembler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.errorcode.SystemErrorCode;
import com.titanium.metadata.exception.DomainException;
import com.titanium.policy.api.request.issuance.SubmitIssuanceRequest;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.valueobject.issuance.IssuanceRequest;

class IssuanceRequestAssemblerTest {

    private final IssuanceRequestAssembler assembler = new IssuanceRequestAssembler();

    @Test
    void preservesBeneficiaryGenderAndMobileInDomainSnapshot() {
        SubmitIssuanceRequest.PartyInput beneficiary = new SubmitIssuanceRequest.PartyInput();
        beneficiary.setName("李四");
        beneficiary.setCertType("CHINA_ID_CARD");
        beneficiary.setCertNo("110101199202022222");
        beneficiary.setGender("FEMALE");
        beneficiary.setMobile("13900139000");
        beneficiary.setBeneficiaryType("DEATH");
        beneficiary.setBeneficiaryOrder(1);
        beneficiary.setShareRatio(new BigDecimal("100"));

        SubmitIssuanceRequest request = new SubmitIssuanceRequest();
        request.setBizNo("BIZ_001");
        request.setBeneficiaryList(List.of(beneficiary));

        IssuanceRequest result = new IssuanceRequestAssembler().toDomainRequest(request, "TENANT_001");

        InsuredPartyList.BeneficiaryInfo snapshot = result.insuredPartyList().beneficiaryList().getFirst();
        assertEquals(CustomerGender.FEMALE, snapshot.gender());
        assertEquals("13900139000", snapshot.phone());
    }

    // ==================== m25-08（DSN-06）新增 ====================

    /** 投保人快照的性别与出生日期不再经 6 参窄构造器静默丢失。 */
    @Test
    void preservesHolderGenderAndBirthDateInDomainSnapshot() {
        SubmitIssuanceRequest.PartyInput holder = party("张三", "110101199001011234");
        holder.setGender("MALE");
        holder.setBirthDate(LocalDateTime.of(1990, 1, 1, 0, 0));

        SubmitIssuanceRequest request = new SubmitIssuanceRequest();
        request.setBizNo("BIZ_HOLDER");
        request.setHolder(holder);

        IssuanceRequest result = assembler.toDomainRequest(request, "TENANT_001");

        InsuredPartyList.HolderInfo snapshot = result.insuredPartyList().holderInfo();
        assertEquals(CustomerGender.MALE, snapshot.gender());
        assertEquals(LocalDate.of(1990, 1, 1), snapshot.birthDate());
    }

    /** 未提供年龄时按出生日期与保单起期推算周岁（精确到日，基准为起期而非当前时间）。 */
    @Test
    void derivesInsuredAgeFromBirthDateUsingPolicyPeriodStart() {
        SubmitIssuanceRequest request = requestWithInsured(insured(null, LocalDateTime.of(1990, 6, 15, 0, 0)));
        request.setPeriodStart(LocalDateTime.of(2025, 6, 14, 0, 0));
        assertEquals(34, insuredAge(assembler.toDomainRequest(request, "TENANT_001")));

        request.setPeriodStart(LocalDateTime.of(2025, 6, 15, 0, 0));
        assertEquals(35, insuredAge(assembler.toDomainRequest(request, "TENANT_001")));
    }

    /** 显式 age 优先于出生日期推算。 */
    @Test
    void prefersExplicitAgeOverBirthDateDerivation() {
        SubmitIssuanceRequest request = requestWithInsured(insured(36, LocalDateTime.of(1990, 6, 15, 0, 0)));
        request.setPeriodStart(LocalDateTime.of(2025, 6, 15, 0, 0));

        assertEquals(36, insuredAge(assembler.toDomainRequest(request, "TENANT_001")));
    }

    /** age 与 birthDate 双缺（或 birthDate 在但起期缺失无法推算）即拒绝装配。 */
    @Test
    void rejectsInsuredWhenNeitherAgeNorBirthDateProvided() {
        SubmitIssuanceRequest missingBoth = requestWithInsured(insured(null, null));
        DomainException exception = assertThrows(DomainException.class,
                () -> assembler.toDomainRequest(missingBoth, "TENANT_001"));
        assertEquals(PolicyErrorCode.ISSUANCE_INSURED_AGE_MISSING, exception.getErrorCode());

        SubmitIssuanceRequest noPeriodStart = requestWithInsured(insured(null, LocalDateTime.of(1990, 6, 15, 0, 0)));
        noPeriodStart.setPeriodStart(null);
        DomainException sameCode = assertThrows(DomainException.class,
                () -> assembler.toDomainRequest(noPeriodStart, "TENANT_001"));
        assertEquals(PolicyErrorCode.ISSUANCE_INSURED_AGE_MISSING, sameCode.getErrorCode());
    }

    /** 负年龄属非法入参，直接拒绝。 */
    @Test
    void rejectsNegativeInsuredAge() {
        SubmitIssuanceRequest request = requestWithInsured(insured(-1, null));

        DomainException exception = assertThrows(DomainException.class,
                () -> assembler.toDomainRequest(request, "TENANT_001"));
        assertEquals(SystemErrorCode.PARAM_INVALID, exception.getErrorCode());
    }

    /** 参与方清单标识由业务流水号确定性派生：同一 bizNo 恒同值、不同 bizNo 不同值、UUID 36 位形态。 */
    @Test
    void derivesStablePartyListIdFromBizNo() {
        String first = assembler.toDomainRequest(requestWithInsured(insured(30, null)), "TENANT_001")
                .insuredPartyList().listId();
        String again = assembler.toDomainRequest(requestWithInsured(insured(30, null)), "TENANT_001")
                .insuredPartyList().listId();
        assertEquals(first, again);

        SubmitIssuanceRequest other = requestWithInsured(insured(30, null));
        other.setBizNo("BIZ_OTHER");
        assertNotEquals(first,
                assembler.toDomainRequest(other, "TENANT_001").insuredPartyList().listId());
        assertEquals(36, first.length());
    }

    /** customerId 缺失时参与方标识保持为空（待 resolver 回填），不得伪造随机标识。 */
    @Test
    void doesNotFabricateParticipantIdsWhenCustomerIdMissing() {
        SubmitIssuanceRequest.PartyInput holder = party("张三", "110101199001011234");
        SubmitIssuanceRequest.PartyInput beneficiary = party("李四", "110101199202022222");
        beneficiary.setBeneficiaryType("DEATH");
        beneficiary.setShareRatio(new BigDecimal("100"));

        SubmitIssuanceRequest request = requestWithInsured(insured(30, null));
        request.setHolder(holder);
        request.setBeneficiaryList(List.of(beneficiary));

        IssuanceRequest result = assembler.toDomainRequest(request, "TENANT_001");

        assertNull(result.insuredPartyList().holderInfo().holderId());
        assertNull(result.insuredPartyList().insuredList().getFirst().insuredId());
        assertNull(result.insuredPartyList().beneficiaryList().getFirst().beneficiaryId());
    }

    /** customerId 存在时参与方标识复用之（holderId/insuredId ≡ customerId 的装配侧语义）。 */
    @Test
    void reusesCustomerIdAsParticipantIdWhenProvided() {
        SubmitIssuanceRequest.PartyInput holder = party("张三", "110101199001011234");
        holder.setCustomerId("CUSTOMER_HOLDER");
        SubmitIssuanceRequest.PartyInput insured = insured(30, null);
        insured.setCustomerId("CUSTOMER_INSURED");

        SubmitIssuanceRequest request = requestWithInsured(insured);
        request.setHolder(holder);

        IssuanceRequest result = assembler.toDomainRequest(request, "TENANT_001");

        assertEquals("CUSTOMER_HOLDER", result.insuredPartyList().holderInfo().holderId());
        assertEquals("CUSTOMER_INSURED", result.insuredPartyList().insuredList().getFirst().insuredId());
    }

    private SubmitIssuanceRequest.PartyInput party(String name, String certNo) {
        SubmitIssuanceRequest.PartyInput input = new SubmitIssuanceRequest.PartyInput();
        input.setName(name);
        input.setCertType("CHINA_ID_CARD");
        input.setCertNo(certNo);
        return input;
    }

    private SubmitIssuanceRequest.PartyInput insured(Integer age, LocalDateTime birthDate) {
        SubmitIssuanceRequest.PartyInput input = party("王五", "110101199506063333");
        input.setAge(age);
        input.setBirthDate(birthDate);
        return input;
    }

    private SubmitIssuanceRequest requestWithInsured(SubmitIssuanceRequest.PartyInput insured) {
        SubmitIssuanceRequest request = new SubmitIssuanceRequest();
        request.setBizNo("BIZ_002");
        request.setPeriodStart(LocalDateTime.of(2025, 6, 15, 0, 0));
        request.setInsuredList(List.of(insured));
        return request;
    }

    private int insuredAge(IssuanceRequest request) {
        return request.insuredPartyList().insuredList().getFirst().age();
    }
}
