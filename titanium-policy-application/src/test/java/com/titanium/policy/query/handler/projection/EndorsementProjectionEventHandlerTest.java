package com.titanium.policy.query.handler.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;
import com.titanium.metadata.enums.customer.CustomerEnum.IdCardType;
import com.titanium.metadata.enums.maintenance.PolicyMaintenanceAction;
import com.titanium.metadata.enums.policy.BeneficiaryType;
import com.titanium.metadata.enums.policy.PolicyServiceRestrictionAction;
import com.titanium.metadata.enums.policy.PolicyServiceRestrictionType;
import com.titanium.metadata.enums.pricing.ChargeDirection;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.common.enums.EndorsementCategory;
import com.titanium.policy.common.enums.FamilyRelation;
import com.titanium.policy.common.enums.PolicyDataUpdateType;
import com.titanium.policy.common.enums.PolicyStatusCode;
import com.titanium.policy.common.enums.PremiumPaymentCycle;
import com.titanium.policy.common.enums.PremiumPaymentMethod;
import com.titanium.policy.common.enums.PremiumPaymentStatus;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.event.PolicyMaintenanceAppliedEvent;
import com.titanium.policy.event.PolicyMaintenanceStateAppliedEvent;
import com.titanium.policy.query.mapper.PolicyViewMapper;
import com.titanium.policy.query.repository.PolicyBeneficiaryViewRepository;
import com.titanium.policy.query.repository.PolicyEndorsementViewRepository;
import com.titanium.policy.query.repository.PolicyInsuredViewRepository;
import com.titanium.policy.query.repository.PolicyProductViewRepository;
import com.titanium.policy.query.repository.PolicyViewRepository;
import com.titanium.policy.query.view.PolicyBeneficiaryView;
import com.titanium.policy.query.view.PolicyEndorsementView;
import com.titanium.policy.query.view.PolicyInsuredView;
import com.titanium.policy.query.view.PolicyProductView;
import com.titanium.policy.query.view.PolicyView;
import com.titanium.policy.valueobject.PremiumPlan;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceAmountFact;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceExecutionState;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceServiceRestriction;
import com.titanium.policy.valueobject.policy.PolicyServiceRestrictions;

class EndorsementProjectionEventHandlerTest {

    @Test
    void projectsMaintenanceStateEventIntoEndorsementAndPolicyVersion() {
        PolicyEndorsementViewRepository endorsementRepository = mock(PolicyEndorsementViewRepository.class);
        PolicyViewRepository policyRepository = mock(PolicyViewRepository.class);
        PolicyProductViewRepository productRepository = mock(PolicyProductViewRepository.class);
        PolicyBeneficiaryViewRepository beneficiaryRepository = mock(PolicyBeneficiaryViewRepository.class);
        PolicyInsuredViewRepository insuredRepository = mock(PolicyInsuredViewRepository.class);
        PolicyView policy = new PolicyView();
        when(endorsementRepository.findById("END_001")).thenReturn(Optional.empty());
        when(policyRepository.findByPolicyIdAndTenantId("POLICY_001", "TENANT_001"))
                .thenReturn(Optional.of(policy));
        PolicyMaintenanceStateAppliedEvent event = stateEvent();

        new EndorsementProjectionEventHandler(
                endorsementRepository, policyRepository, productRepository, mock(PolicyViewMapper.class),
                beneficiaryRepository, insuredRepository).on(event);

        ArgumentCaptor<PolicyEndorsementView> endorsement =
                ArgumentCaptor.forClass(PolicyEndorsementView.class);
        verify(endorsementRepository).save(endorsement.capture());
        verify(policyRepository).save(policy);
        assertEquals("POLICY_SUSPENSION", endorsement.getValue().getUpdateType());
        assertEquals(EndorsementCategory.LIFECYCLE.getCode(), endorsement.getValue().getCategory());
        assertEquals(8, endorsement.getValue().getPolicyVersion());
        assertEquals("MAINTENANCE_001", endorsement.getValue().getSourceMaintenanceId());
        assertEquals(8, policy.getCurrentVersion());
    }

    @Test
    void projectsMaintenanceCoverageIntoPolicyAndProductViews() {
        PolicyEndorsementViewRepository endorsementRepository = mock(PolicyEndorsementViewRepository.class);
        PolicyViewRepository policyRepository = mock(PolicyViewRepository.class);
        PolicyProductViewRepository productRepository = mock(PolicyProductViewRepository.class);
        PolicyBeneficiaryViewRepository beneficiaryRepository = mock(PolicyBeneficiaryViewRepository.class);
        PolicyInsuredViewRepository insuredRepository = mock(PolicyInsuredViewRepository.class);
        PolicyView policy = new PolicyView();
        PolicyProductView productView = new PolicyProductView();
        productView.setPolicyProductId("LINE_001");
        when(endorsementRepository.findById("END_COVERAGE_001")).thenReturn(Optional.empty());
        when(policyRepository.findByPolicyIdAndTenantId("POLICY_001", "TENANT_001"))
                .thenReturn(Optional.of(policy));
        when(productRepository.findByPolicyIdAndTenantIdOrderByLineNoAsc("POLICY_001", "TENANT_001"))
                .thenReturn(List.of(productView));

        new EndorsementProjectionEventHandler(
                endorsementRepository, policyRepository, productRepository, mock(PolicyViewMapper.class),
                beneficiaryRepository, insuredRepository)
                .on(coverageEvent());

        assertEquals(new BigDecimal("120000.00"), policy.getSumInsured());
        assertEquals(new BigDecimal("120000.00"), productView.getSumInsured());
        assertEquals(8, policy.getCurrentVersion());
        verify(productRepository).save(productView);
        verify(policyRepository).save(policy);
    }

    @Test
    void replacesBeneficiaryProjectionFromMaintenanceExecutionState() {
        PolicyEndorsementViewRepository endorsementRepository = mock(PolicyEndorsementViewRepository.class);
        PolicyViewRepository policyRepository = mock(PolicyViewRepository.class);
        PolicyProductViewRepository productRepository = mock(PolicyProductViewRepository.class);
        PolicyBeneficiaryViewRepository beneficiaryRepository = mock(PolicyBeneficiaryViewRepository.class);
        PolicyInsuredViewRepository insuredRepository = mock(PolicyInsuredViewRepository.class);
        PolicyView policy = new PolicyView();
        when(endorsementRepository.findById("END_BENEFICIARY_001")).thenReturn(Optional.empty());
        when(policyRepository.findByPolicyIdAndTenantId("POLICY_001", "TENANT_001"))
                .thenReturn(Optional.of(policy));

        new EndorsementProjectionEventHandler(
                endorsementRepository, policyRepository, productRepository, mock(PolicyViewMapper.class),
                beneficiaryRepository, insuredRepository)
                .on(beneficiaryEvent());

        verify(beneficiaryRepository).deleteByPolicyIdAndTenantId("POLICY_001", "TENANT_001");
        verify(beneficiaryRepository).flush();
        ArgumentCaptor<PolicyBeneficiaryView> beneficiary =
                ArgumentCaptor.forClass(PolicyBeneficiaryView.class);
        verify(beneficiaryRepository).save(beneficiary.capture());
        assertEquals("b".repeat(32), beneficiary.getValue().getId());
        assertEquals("李四", beneficiary.getValue().getBeneficiaryName());
        assertEquals("DEATH", beneficiary.getValue().getBeneficiaryType());
        assertEquals(1, beneficiary.getValue().getOrderNo());
        assertEquals(new BigDecimal("100"), beneficiary.getValue().getShareRatio());
        assertEquals("TENANT_001", beneficiary.getValue().getTenantId());
    }

    @Test
    void projectsPaymentMethodIntoPolicyViewEvenWithoutProductChanges() {
        PolicyEndorsementViewRepository endorsementRepository = mock(PolicyEndorsementViewRepository.class);
        PolicyViewRepository policyRepository = mock(PolicyViewRepository.class);
        PolicyProductViewRepository productRepository = mock(PolicyProductViewRepository.class);
        PolicyBeneficiaryViewRepository beneficiaryRepository = mock(PolicyBeneficiaryViewRepository.class);
        PolicyInsuredViewRepository insuredRepository = mock(PolicyInsuredViewRepository.class);
        PolicyView policy = new PolicyView();
        policy.setPaymentMethod(PremiumPaymentMethod.SINGLE_PAYMENT.getCode());
        when(endorsementRepository.findById("END_PAYMENT_001")).thenReturn(Optional.empty());
        when(policyRepository.findByPolicyIdAndTenantId("POLICY_001", "TENANT_001"))
                .thenReturn(Optional.of(policy));

        new EndorsementProjectionEventHandler(
                endorsementRepository, policyRepository, productRepository, mock(PolicyViewMapper.class),
                beneficiaryRepository, insuredRepository)
                .on(paymentMethodEvent());

        // 🔴 本事件的执行状态不带险种段（policyProducts 为 null）。premiumPlan 分支必须位于
        // applyExecutionState 中 policyProducts 的早返回之前，否则「仅改缴费方式」的保全读模型永不更新
        // ——这正是缺口 G7 的成因，本断言即为该顺序的回归锁。
        assertEquals(PremiumPaymentMethod.INSTALLMENT_PAYMENT.getCode(), policy.getPaymentMethod());
        verify(policyRepository).save(policy);
        verifyNoInteractions(productRepository);
    }

    @Test
    void projectsHolderIdentityIntoPolicyViewEvenWithoutProductChanges() {
        PolicyEndorsementViewRepository endorsementRepository = mock(PolicyEndorsementViewRepository.class);
        PolicyViewRepository policyRepository = mock(PolicyViewRepository.class);
        PolicyProductViewRepository productRepository = mock(PolicyProductViewRepository.class);
        PolicyBeneficiaryViewRepository beneficiaryRepository = mock(PolicyBeneficiaryViewRepository.class);
        PolicyInsuredViewRepository insuredRepository = mock(PolicyInsuredViewRepository.class);
        PolicyView policy = new PolicyView();
        when(endorsementRepository.findById("END_HOLDER_001")).thenReturn(Optional.empty());
        when(policyRepository.findByPolicyIdAndTenantId("POLICY_001", "TENANT_001"))
                .thenReturn(Optional.of(policy));

        new EndorsementProjectionEventHandler(
                endorsementRepository, policyRepository, productRepository, mock(PolicyViewMapper.class),
                beneficiaryRepository, insuredRepository)
                .on(holderEvent());

        // 🔴 与缴费方式同理：本事件的执行状态不带险种段（policyProducts 为 null）。holder 分支必须位于
        // applyExecutionState 中 policyProducts 的早返回之前，否则「仅改投保人字段」的保全读模型永不更新。
        // 六项一次性回写，避免只回写其中一项造成读模型半新半旧。
        assertEquals("李四", policy.getPolicyHolderName());
        assertEquals("CHINA_ID_CARD", policy.getPolicyHolderIdType());
        assertEquals("ID-9", policy.getPolicyHolderIdNo());
        assertEquals("13900000000", policy.getPolicyHolderPhone());
        assertEquals("FEMALE", policy.getPolicyHolderGender());
        assertEquals(LocalDate.of(1990, 5, 20), policy.getPolicyHolderBirthDate());
        verify(policyRepository).save(policy);
        verifyNoInteractions(productRepository);
    }

    @Test
    void replacesInsuredProjectionFromMaintenanceExecutionState() {
        PolicyEndorsementViewRepository endorsementRepository = mock(PolicyEndorsementViewRepository.class);
        PolicyViewRepository policyRepository = mock(PolicyViewRepository.class);
        PolicyProductViewRepository productRepository = mock(PolicyProductViewRepository.class);
        PolicyBeneficiaryViewRepository beneficiaryRepository = mock(PolicyBeneficiaryViewRepository.class);
        PolicyInsuredViewRepository insuredRepository = mock(PolicyInsuredViewRepository.class);
        PolicyView policy = new PolicyView();
        when(endorsementRepository.findById("END_INSURED_001")).thenReturn(Optional.empty());
        when(policyRepository.findByPolicyIdAndTenantId("POLICY_001", "TENANT_001"))
                .thenReturn(Optional.of(policy));

        new EndorsementProjectionEventHandler(
                endorsementRepository, policyRepository, productRepository, mock(PolicyViewMapper.class),
                beneficiaryRepository, insuredRepository)
                .on(insuredEvent());

        // 🔴 被保险人身份要素变更同样不带险种段（policyProducts 为 null），分支必须位于早返回之前。
        // 主键即保全集合字段的对象标识（聚合内 insuredId），与出单投影同源——执行器按该标识定位元素，
        // 两侧派生规则一旦分叉，受理成功的保全项会在生效环节失败关闭。
        verify(insuredRepository).deleteByPolicyIdAndTenantId("POLICY_001", "TENANT_001");
        verify(insuredRepository).flush();
        ArgumentCaptor<PolicyInsuredView> insured = ArgumentCaptor.forClass(PolicyInsuredView.class);
        verify(insuredRepository).save(insured.capture());
        assertEquals("insured-1", insured.getValue().getId());
        assertEquals("POLICY_001", insured.getValue().getPolicyId());
        assertEquals("李四", insured.getValue().getInsuredName());
        assertEquals("CHINA_ID_CARD", insured.getValue().getIdType());
        assertEquals("ID-2", insured.getValue().getIdNo());
        assertEquals(30, insured.getValue().getAge());
        assertEquals("FEMALE", insured.getValue().getGender());
        assertEquals("13700000000", insured.getValue().getPhone());
        assertEquals("SELF", insured.getValue().getRelation());
        assertEquals("SELF", insured.getValue().getFamilyRelation());
        assertEquals("TENANT_001", insured.getValue().getTenantId());
        // 保单主视图的冗余列与参与方投影同源（取首位被保险人）
        assertEquals("李四", policy.getInsuredName());
        verify(policyRepository).save(policy);
        verifyNoInteractions(productRepository);
    }

    /**
     * 🔴 G11/g11-04 ⑥ / AC-05：多主险段快照**不得**覆盖保单级保额冗余列——按段逐个覆盖会随快照
     * 顺序漂移（末段胜出，其保额为空时还会把列清空），故多主险时保持既有列值；段级保额以逐段回写
     * （{@code t_policy_product}）为准，本断言同时锁定正反两序结果一致。
     */
    @Test
    void keepsPolicyLevelSumInsuredWhenSnapshotContainsMultipleMainProducts() {
        MultiMainCoverage forward = runMultiMainCoverageSnapshot(List.of(
                mainProduct("LINE_001", 1, "PRODUCT_CMP", "120000"),
                mainProduct("LINE_002", 2, "PRODUCT_CMR", "80000")));
        MultiMainCoverage reversed = runMultiMainCoverageSnapshot(List.of(
                mainProduct("LINE_002", 2, "PRODUCT_CMR", "80000"),
                mainProduct("LINE_001", 1, "PRODUCT_CMP", "120000")));

        assertEquals(new BigDecimal("999999.00"), forward.policy().getSumInsured(),
                "多主险时保单级保额冗余列无对应语义，必须保持既有值（不得被任一段覆盖）");
        assertEquals(new BigDecimal("999999.00"), reversed.policy().getSumInsured(),
                "🔴 正反两序结果必须一致——若随顺序漂移即为「末段胜出」缺陷复现");
        assertEquals(new BigDecimal("120000.00"), forward.line1View().getSumInsured(),
                "段级保额仍须逐段回写（多主险的权威表述在段表）");
        assertEquals(new BigDecimal("80000.00"), forward.line2View().getSumInsured());
        assertEquals(new BigDecimal("120000.00"), reversed.line1View().getSumInsured());
        assertEquals(new BigDecimal("80000.00"), reversed.line2View().getSumInsured());
    }

    /** ⑥ 用例的独立场景：既有保单级保额 999999 + 两主险段各自的读模型行。 */
    private MultiMainCoverage runMultiMainCoverageSnapshot(List<PolicyProduct> products) {
        PolicyEndorsementViewRepository endorsementRepository = mock(PolicyEndorsementViewRepository.class);
        PolicyViewRepository policyRepository = mock(PolicyViewRepository.class);
        PolicyProductViewRepository productRepository = mock(PolicyProductViewRepository.class);
        PolicyView policy = new PolicyView();
        policy.setSumInsured(new BigDecimal("999999.00"));
        PolicyProductView line1View = new PolicyProductView();
        line1View.setPolicyProductId("LINE_001");
        PolicyProductView line2View = new PolicyProductView();
        line2View.setPolicyProductId("LINE_002");
        when(endorsementRepository.findById("END_COVERAGE_001")).thenReturn(Optional.empty());
        when(policyRepository.findByPolicyIdAndTenantId("POLICY_001", "TENANT_001"))
                .thenReturn(Optional.of(policy));
        when(productRepository.findByPolicyIdAndTenantIdOrderByLineNoAsc("POLICY_001", "TENANT_001"))
                .thenReturn(List.of(line1View, line2View));

        new EndorsementProjectionEventHandler(
                endorsementRepository, policyRepository, productRepository, mock(PolicyViewMapper.class),
                mock(PolicyBeneficiaryViewRepository.class), mock(PolicyInsuredViewRepository.class))
                .on(coverageEvent(products));
        return new MultiMainCoverage(policy, line1View, line2View);
    }

    /** 多主险保额快照用例的断言载体。 */
    private record MultiMainCoverage(PolicyView policy, PolicyProductView line1View, PolicyProductView line2View) {
    }

    /** 建一个带保额的测试主险段。 */
    private static PolicyProduct mainProduct(String lineId, int lineNo, String productId, String sumInsured) {
        return new PolicyProduct(lineId, lineNo, ProductCategory.MAIN, null, productId, "CODE_" + lineId,
                "测试主险", "V1.0", "PLAN_V1", null, Money.of(new BigDecimal(sumInsured), "CNY"),
                Money.of(new BigDecimal("1000"), "CNY"), null, null, null, null,
                List.of(), List.of(), List.of());
    }

    /** 金额型保全（追加/减额缴费、追加投资、部分领取）不带字段变更与状态动作，其合同效力<b>就是这笔收付</b>；
     * 差额四列必须落批单读模型，否则事后无从回答「这次批改动了多少钱」。
     */
    @Test
    void projectsRecordedAmountFactIntoEndorsementAmountColumns() {
        PolicyEndorsementViewRepository endorsementRepository = mock(PolicyEndorsementViewRepository.class);
        PolicyViewRepository policyRepository = mock(PolicyViewRepository.class);
        when(endorsementRepository.findById("END_AMOUNT_001")).thenReturn(Optional.empty());
        when(policyRepository.findByPolicyIdAndTenantId("POLICY_001", "TENANT_001"))
                .thenReturn(Optional.of(new PolicyView()));

        new EndorsementProjectionEventHandler(
                endorsementRepository, policyRepository, mock(PolicyProductViewRepository.class),
                mock(PolicyViewMapper.class), mock(PolicyBeneficiaryViewRepository.class),
                mock(PolicyInsuredViewRepository.class))
                .on(amountEvent());

        ArgumentCaptor<PolicyEndorsementView> endorsement =
                ArgumentCaptor.forClass(PolicyEndorsementView.class);
        verify(endorsementRepository).save(endorsement.capture());
        assertEquals("ADDITIONAL_PAYMENT", endorsement.getValue().getAmountItemCode());
        assertEquals(0, endorsement.getValue().getAmountValue().compareTo(new BigDecimal("1000.00")));
        assertEquals(ChargeDirection.DEBIT.getCode(), endorsement.getValue().getAmountDirection());
        assertEquals("CNY", endorsement.getValue().getAmountCurrency());
    }

    /**
     * 受限类保全（挂失/解除挂失）必须把受限清单落到保单读模型列——该列是保全域受理前置准入与
     * 退保/贷款准入的数据源。写漏则读侧与下游域恒答「无受限」：受限保单照样能退保，且全链路不报错。
     * <p>
     * 🔴 解除必须把列<b>清空</b>而非「无受限就跳过写入」——跳过会让解除挂在旧值上，读侧永远认为该保单受限。
     * </p>
     */
    @Test
    void projectsServiceRestrictionLifecycleIntoPolicyViewColumn() {
        PolicyEndorsementViewRepository endorsementRepository = mock(PolicyEndorsementViewRepository.class);
        PolicyViewRepository policyRepository = mock(PolicyViewRepository.class);
        PolicyView policy = new PolicyView();
        when(endorsementRepository.findById("END_LOSS_001")).thenReturn(Optional.empty());
        when(endorsementRepository.findById("END_RELEASE_001")).thenReturn(Optional.empty());
        when(policyRepository.findByPolicyIdAndTenantId("POLICY_001", "TENANT_001"))
                .thenReturn(Optional.of(policy));
        EndorsementProjectionEventHandler handler = new EndorsementProjectionEventHandler(
                endorsementRepository, policyRepository, mock(PolicyProductViewRepository.class),
                mock(PolicyViewMapper.class), mock(PolicyBeneficiaryViewRepository.class),
                mock(PolicyInsuredViewRepository.class));

        handler.on(restrictionEvent(restrictionsAfter(PolicyServiceRestrictionAction.APPLY), "END_LOSS_001"));

        assertEquals("LOSS_REPORT", policy.getServiceRestrictionTypes(),
                "生效中的受限种类码须写入读模型列，否则读侧恒答「无受限」");

        handler.on(restrictionEvent(
                restrictionsAfter(PolicyServiceRestrictionAction.RELEASE), "END_RELEASE_001"));

        assertNull(policy.getServiceRestrictionTypes(),
                "🔴 解除后必须清空列：跳过写入会让解除挂在旧值上，受限成为读侧永不解除");
    }

    /**
     * 字段型保全（事件 amountFact 为空）必须把既有行上的金额四列显式置空——读模型按批单号 upsert，
     * 沿用旧行时残留的上一笔差额会让一条「改联系方式」的批单显示成「收了 1000 元」。
     */
    @Test
    void clearsAmountColumnsWhenEndorsementCarriesNoAmountFact() {
        PolicyEndorsementViewRepository endorsementRepository = mock(PolicyEndorsementViewRepository.class);
        PolicyViewRepository policyRepository = mock(PolicyViewRepository.class);
        PolicyEndorsementView existing = new PolicyEndorsementView();
        existing.setAmountItemCode("ADDITIONAL_PAYMENT");
        existing.setAmountValue(new BigDecimal("1000.00"));
        existing.setAmountDirection("1");
        existing.setAmountCurrency("CNY");
        when(endorsementRepository.findById("END_COVERAGE_001")).thenReturn(Optional.of(existing));
        when(policyRepository.findByPolicyIdAndTenantId("POLICY_001", "TENANT_001"))
                .thenReturn(Optional.of(new PolicyView()));

        new EndorsementProjectionEventHandler(
                endorsementRepository, policyRepository, mock(PolicyProductViewRepository.class),
                mock(PolicyViewMapper.class), mock(PolicyBeneficiaryViewRepository.class),
                mock(PolicyInsuredViewRepository.class))
                .on(coverageEvent());

        assertNull(existing.getAmountItemCode());
        assertNull(existing.getAmountValue());
        assertNull(existing.getAmountDirection());
        assertNull(existing.getAmountCurrency());
    }

    /** 金额型保有效力载荷：只有一笔已登记差额，无字段变更、无状态动作，执行状态快照与保全前一致。 */
    private PolicyMaintenanceAppliedEvent amountEvent() {
        LocalDateTime appliedAt = LocalDateTime.parse("2026-09-28T10:00:00");
        return new PolicyMaintenanceAppliedEvent(
                "POLICY_001", "REQUEST_AMOUNT_001", "a".repeat(64), "MAINTENANCE_AMOUNT_001",
                "END_AMOUNT_001", PolicyDataUpdateType.ADDITIONAL_PAYMENT,
                EndorsementCategory.PREMIUM_TERMS, 7, 8, appliedAt,
                "maintenance=MAINTENANCE_AMOUNT_001;items=ADDITIONAL_PAYMENT;fields=;action=NONE;amount=DEBIT:1000.00:CNY",
                new PolicyMaintenanceAmountFact(
                        "ADDITIONAL_PAYMENT", new BigDecimal("1000.00"), ChargeDirection.DEBIT, "CNY",
                        "POSTING-1"),
                null, "b".repeat(64), "c".repeat(64), "axon-event://policy/POLICY_001/8",
                "d".repeat(64), "e".repeat(64), List.of(),
                new PolicyMaintenanceExecutionState(null, null),
                appliedAt, "operator-1", "TENANT_001");
    }

    private PolicyMaintenanceStateAppliedEvent stateEvent() {
        LocalDateTime appliedAt = LocalDateTime.parse("2026-08-25T16:00:00");
        return new PolicyMaintenanceStateAppliedEvent(
                "POLICY_001", "REQUEST_001", "a".repeat(64), "MAINTENANCE_001", "END_001",
                "POLICY_SUSPENSION", EndorsementCategory.LIFECYCLE, 7, 8, appliedAt,
                "保单暂停", "b".repeat(64), "c".repeat(64), "axon-event://policy/POLICY_001/8",
                "d".repeat(64), "e".repeat(64), List.of(), null, PolicyMaintenanceAction.SUSPEND,
                PolicyStatusCode.EFFECTIVE, PolicyStatusCode.SUSPENDED,
                "客户申请暂停", null, appliedAt, "operator-1", "TENANT_001");
    }

    private PolicyMaintenanceAppliedEvent coverageEvent() {
        Money premium = Money.of(new BigDecimal("1000"), "CNY");
        PolicyProduct product = new PolicyProduct(
                "LINE_001", 1, ProductCategory.MAIN, null, "PRODUCT_001", "P001", "测试主险",
                "V1.0", "PLAN_V1", null, Money.of(new BigDecimal("120000"), "CNY"), premium,
                null, null, null, null, List.of(), List.of(), List.of());
        return coverageEvent(List.of(product));
    }

    /** 带指定险种段的保额变更载荷（多主险 ⑥ 用例注入不同段组合）。 */
    private PolicyMaintenanceAppliedEvent coverageEvent(List<PolicyProduct> products) {
        LocalDateTime appliedAt = LocalDateTime.parse("2026-08-25T16:00:00");
        return new PolicyMaintenanceAppliedEvent(
                "POLICY_001", "REQUEST_COVERAGE_001", "a".repeat(64), "MAINTENANCE_COVERAGE_001",
                "END_COVERAGE_001", PolicyDataUpdateType.COVERAGE_AMOUNT_CHANGE,
                EndorsementCategory.SUM_INSURED, 7, 8, appliedAt, "保额变更",
                null, null, "b".repeat(64), "c".repeat(64), "axon-event://policy/POLICY_001/8",
                "d".repeat(64), "e".repeat(64), List.of(),
                new PolicyMaintenanceExecutionState(null, products),
                appliedAt, "operator-1", "TENANT_001");
    }

    private PolicyMaintenanceAppliedEvent beneficiaryEvent() {
        LocalDateTime appliedAt = LocalDateTime.parse("2026-08-28T10:00:00");
        InsuredPartyList.BeneficiaryInfo beneficiary = new InsuredPartyList.BeneficiaryInfo(
                null, "b".repeat(32), "李四", null, null, null, null, BeneficiaryType.DEATH, 1, 1.0d);
        InsuredPartyList parties = new InsuredPartyList(
                "PARTIES_001", null, List.of(), List.of(beneficiary));
        return new PolicyMaintenanceAppliedEvent(
                "POLICY_001", "REQUEST_BENEFICIARY_001", "a".repeat(64), "MAINTENANCE_BENEFICIARY_001",
                "END_BENEFICIARY_001", PolicyDataUpdateType.BENEFICIARY_CHANGE,
                EndorsementCategory.PARTY, 7, 8, appliedAt, "受益人变更",
                null, null, "b".repeat(64), "c".repeat(64), "axon-event://policy/POLICY_001/8",
                "d".repeat(64), "e".repeat(64), List.of(),
                new PolicyMaintenanceExecutionState(parties, null),
                appliedAt, "operator-1", "TENANT_001");
    }

    /** 投保人身份要素变更保全：执行状态为批改后的完整参与方快照，且不带险种段。 */
    private PolicyMaintenanceAppliedEvent holderEvent() {
        LocalDateTime appliedAt = LocalDateTime.parse("2026-09-14T10:00:00");
        InsuredPartyList.HolderInfo holder = new InsuredPartyList.HolderInfo(
                "CUSTOMER_001", "HOLDER_001", "李四", IdCardType.CHINA_ID_CARD, "ID-9", "13900000000",
                CustomerGender.FEMALE, LocalDate.of(1990, 5, 20));
        InsuredPartyList.BeneficiaryInfo beneficiary = new InsuredPartyList.BeneficiaryInfo(
                null, "b".repeat(32), "王五", null, null, null, null, BeneficiaryType.DEATH, 1, 1.0d);
        InsuredPartyList parties = new InsuredPartyList(
                "PARTIES_001", holder, List.of(), List.of(beneficiary));
        return new PolicyMaintenanceAppliedEvent(
                "POLICY_001", "REQUEST_HOLDER_001", "a".repeat(64), "MAINTENANCE_HOLDER_001",
                "END_HOLDER_001", PolicyDataUpdateType.HOLDER_CHANGE,
                EndorsementCategory.PARTY, 7, 8, appliedAt, "投保人变更",
                null, null, "b".repeat(64), "c".repeat(64), "axon-event://policy/POLICY_001/8",
                "d".repeat(64), "e".repeat(64), List.of(),
                new PolicyMaintenanceExecutionState(parties, null),
                appliedAt, "operator-1", "TENANT_001");
    }

    /** 被保险人身份要素变更保全：执行状态为批改后的完整参与方快照，且不带险种段。 */
    private PolicyMaintenanceAppliedEvent insuredEvent() {
        LocalDateTime appliedAt = LocalDateTime.parse("2026-09-14T10:00:00");
        InsuredPartyList.InsuredInfo insured = new InsuredPartyList.InsuredInfo(
                "CUSTOMER_002", "insured-1", "李四", IdCardType.CHINA_ID_CARD, "ID-2", 30,
                CustomerGender.FEMALE, "13700000000", "SELF", FamilyRelation.SELF);
        InsuredPartyList parties = new InsuredPartyList(
                "PARTIES_001", null, List.of(insured), List.of());
        return new PolicyMaintenanceAppliedEvent(
                "POLICY_001", "REQUEST_INSURED_001", "a".repeat(64), "MAINTENANCE_INSURED_001",
                "END_INSURED_001", PolicyDataUpdateType.INSURED_INFO_CHANGE,
                EndorsementCategory.PARTY, 7, 8, appliedAt, "被保险人变更",
                null, null, "b".repeat(64), "c".repeat(64), "axon-event://policy/POLICY_001/8",
                "d".repeat(64), "e".repeat(64), List.of(),
                new PolicyMaintenanceExecutionState(parties, null),
                appliedAt, "operator-1", "TENANT_001");
    }

    /** 缴费方式变更保全：执行状态只带保费计划，不带险种段与参与方。 */
    private PolicyMaintenanceAppliedEvent paymentMethodEvent() {
        LocalDateTime appliedAt = LocalDateTime.parse("2026-08-29T10:00:00");
        PremiumPlan plan = new PremiumPlan(
                Money.of(new BigDecimal("1000"), "CNY"), PremiumPaymentMethod.INSTALLMENT_PAYMENT,
                PremiumPaymentCycle.MONTHLY, LocalDateTime.parse("2026-09-01T00:00:00"),
                PremiumPaymentStatus.UNPAID);
        return new PolicyMaintenanceAppliedEvent(
                "POLICY_001", "REQUEST_PAYMENT_001", "a".repeat(64), "MAINTENANCE_PAYMENT_001",
                "END_PAYMENT_001", PolicyDataUpdateType.PAYMENT_METHOD_CHANGE,
                EndorsementCategory.PREMIUM_TERMS, 7, 8, appliedAt, "缴费方式变更",
                null, null, "b".repeat(64), "c".repeat(64), "axon-event://policy/POLICY_001/8",
                "d".repeat(64), "e".repeat(64), List.of(),
                new PolicyMaintenanceExecutionState(null, null, plan),
                appliedAt, "operator-1", "TENANT_001");
    }

    /** 挂失类受限载荷：动作由调用方给定（施加 / 解除）。 */
    private static PolicyMaintenanceServiceRestriction lossReport(PolicyServiceRestrictionAction action) {
        return new PolicyMaintenanceServiceRestriction(PolicyServiceRestrictionType.LOSS_REPORT, action);
    }

    /** 受限清单推进：施加后即受限；解除后清单复归「无受限」（条目保留，故列须清空而非累积）。 */
    private static PolicyServiceRestrictions restrictionsAfter(PolicyServiceRestrictionAction action) {
        LocalDateTime effectiveAt = LocalDateTime.parse("2026-09-29T10:00:00");
        PolicyServiceRestrictions restricted = PolicyServiceRestrictions.EMPTY
                .restrict(lossReport(PolicyServiceRestrictionAction.APPLY), effectiveAt);
        return action == PolicyServiceRestrictionAction.APPLY
                ? restricted
                : restricted.release(lossReport(PolicyServiceRestrictionAction.RELEASE),
                        effectiveAt.plusDays(1));
    }

    /** 受限类保全事件：与其它事件同构，变更实质就是 serviceRestrictionsAfter 这一份完整清单。 */
    private PolicyMaintenanceAppliedEvent restrictionEvent(
            PolicyServiceRestrictions restrictionsAfter,
            String endorsementNo) {
        LocalDateTime appliedAt = LocalDateTime.parse("2026-09-29T10:00:00");
        return new PolicyMaintenanceAppliedEvent(
                "POLICY_001", "REQUEST_" + endorsementNo, "a".repeat(64), "MAINTENANCE_LOSS_001",
                endorsementNo, PolicyDataUpdateType.POLICY_INFO_CHANGE, EndorsementCategory.INFO,
                7, 8, appliedAt, "挂失/解除挂失",
                null, restrictionsAfter, "b".repeat(64), "c".repeat(64),
                "axon-event://policy/POLICY_001/8",
                "d".repeat(64), "e".repeat(64), List.of(),
                new PolicyMaintenanceExecutionState(null, null),
                appliedAt, "operator-1", "TENANT_001");
    }
}
