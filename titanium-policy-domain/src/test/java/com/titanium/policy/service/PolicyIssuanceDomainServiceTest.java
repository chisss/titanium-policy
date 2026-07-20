package com.titanium.policy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.aggregate.Insurance;
import com.titanium.policy.service.impl.PolicyIssuanceDomainServiceImpl;
import com.titanium.policy.valueobject.insurance.InsuranceBasicInfo;
import com.titanium.policy.valueobject.insurance.PolicyIssuanceDecision;
import com.titanium.policy.valueobject.insurance.UnderwritingResult;

/**
 * 承保领域服务单元测试
 * <p>
 * 样板要点：领域服务无 Port/无容器依赖，可直接 {@code new} 出来用纯 JUnit 测试，
 * 无需 {@code @SpringBootTest}、无需 Mock 任何基础设施——这正是「纯领域服务」的可测性红利。
 * </p>
 */
class PolicyIssuanceDomainServiceTest {

    private PolicyIssuanceDomainService domainService;

    private Insurance approvedInsurance;

    @BeforeEach
    void setUp() {
        // 领域服务无外部依赖，直接实例化
        domainService = new PolicyIssuanceDomainServiceImpl();

        // 构造一张承保要素齐备的投保单聚合（SuperBuilder 直接构建，不走事件溯源）
        InsuranceBasicInfo basicInfo = new InsuranceBasicInfo("holder-001", 1, Money.of(new java.math.BigDecimal("1200"),
                "CNY"), LocalDateTime.of(2026, 7, 1, 0, 0), LocalDateTime.of(2027, 7, 1, 0, 0),
                List.of("PROD_A"), 0);
        approvedInsurance = Insurance.builder().insuranceId("INS-001").insuranceNo("INS202607010001")
                .policyForm(PolicyForm.INDIVIDUAL).basicInfo(basicInfo).tenantId("tenant-001").build();
    }

    @Test
    @DisplayName("核保通过(ACCEPT)：可承保且保单要素由投保单推导")
    void shouldAcceptWhenUnderwritingAccepted() {
        UnderwritingResult result = new UnderwritingResult("UW-1", ConclusionType.ACCEPT, "通过", "uw-user",
                LocalDateTime.now(), null, null);

        PolicyIssuanceDecision decision = domainService.decideIssuance(approvedInsurance, result);

        assertTrue(decision.acceptable());
        assertEquals(PolicyForm.INDIVIDUAL, decision.policyForm());
        assertEquals("holder-001", decision.holderId());
        assertEquals(Money.of(new java.math.BigDecimal("1200"), "CNY"), decision.premium());
        assertNull(decision.underwritingCondition(), "ACCEPT 不应携带承保条件");
    }

    @Test
    @DisplayName("修改条件承保(MODIFY)：可承保且携带核保加费条件")
    void shouldCarryConditionWhenModify() {
        UnderwritingResult result = new UnderwritingResult("UW-2", ConclusionType.MODIFY, "加费承保", "uw-user",
                LocalDateTime.now(), "加费30%", new java.math.BigDecimal("0.30"));

        PolicyIssuanceDecision decision = domainService.decideIssuance(approvedInsurance, result);

        assertTrue(decision.acceptable());
        assertEquals("加费30%", decision.underwritingCondition());
    }

    @Test
    @DisplayName("核保拒绝(REJECT)：不可承保")
    void shouldRejectWhenUnderwritingRejected() {
        UnderwritingResult result = new UnderwritingResult("UW-3", ConclusionType.REJECT, "风险过高", "uw-user",
                LocalDateTime.now(), null, null);

        PolicyIssuanceDecision decision = domainService.decideIssuance(approvedInsurance, result);

        assertFalse(decision.acceptable());
        assertEquals("核保拒绝，不可承保", decision.rejectReason());
    }

    @Test
    @DisplayName("核保暂缓(POSTPONE)：不可承保，需人工介入")
    void shouldRejectWhenPostpone() {
        UnderwritingResult result = new UnderwritingResult("UW-4", ConclusionType.POSTPONE, "待补充材料", "uw-user",
                LocalDateTime.now(), null, null);

        PolicyIssuanceDecision decision = domainService.decideIssuance(approvedInsurance, result);

        assertFalse(decision.acceptable());
        assertTrue(decision.rejectReason().contains("暂缓"));
    }

    @Test
    @DisplayName("核保结果缺失：不可承保")
    void shouldRejectWhenResultMissing() {
        PolicyIssuanceDecision decision = domainService.decideIssuance(approvedInsurance, null);

        assertFalse(decision.acceptable());
        assertEquals("缺少核保结论，不可承保", decision.rejectReason());
    }

    @Test
    @DisplayName("canIssueByConclusion：ACCEPT/MODIFY 可承保，REJECT/POSTPONE/缺失不可（与 decideIssuance 准入口径一致）")
    void shouldJudgeConclusionConsistently() {
        assertTrue(domainService.canIssueByConclusion(conclusion(ConclusionType.ACCEPT)));
        assertTrue(domainService.canIssueByConclusion(conclusion(ConclusionType.MODIFY)));
        assertFalse(domainService.canIssueByConclusion(conclusion(ConclusionType.REJECT)));
        assertFalse(domainService.canIssueByConclusion(conclusion(ConclusionType.POSTPONE)));
        assertFalse(domainService.canIssueByConclusion(null), "核保结果缺失不可承保");
        assertFalse(domainService.canIssueByConclusion(new UnderwritingResult("UW-X", null, null, null,
                LocalDateTime.now(), null, null)), "核保结论为空不可承保");
    }

    private UnderwritingResult conclusion(ConclusionType conclusionType) {
        return new UnderwritingResult("UW-C", conclusionType, "结论", "uw-user", LocalDateTime.now(), null, null);
    }
}
