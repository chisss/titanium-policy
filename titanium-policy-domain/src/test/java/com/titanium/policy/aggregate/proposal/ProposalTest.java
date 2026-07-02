package com.titanium.policy.aggregate.proposal;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.aggregate.Proposal;
import com.titanium.policy.entity.proposal.ProposalHolder;
import com.titanium.policy.entity.proposal.ProposalSubject;
import com.titanium.policy.exception.PolicyBusinessRuleException;
import com.titanium.policy.valueobject.proposal.ProposalBasicInfo;
import com.titanium.policy.valueobject.proposal.ProposalStatus;

/**
 * 投保意向单聚合测试
 */
class ProposalTest {
    private Proposal proposal;
    private ProposalBasicInfo basicInfo;
    private ProposalHolder applicant;
    private ProposalSubject subject;

    @BeforeEach
    void setUp() {
        // 初始化基本信息
        basicInfo = new ProposalBasicInfo(
                "customer-123",
                Money.of(new BigDecimal(100000), "CNY"),
                Money.of(new BigDecimal(5000), "CNY"),
                LocalDateTime.now(),
                LocalDateTime.now().plusYears(1),
                "PROD-001"
        );

        // 初始化申请人
        applicant = new ProposalHolder(
                "applicant-123",
                "张三",
                com.titanium.metadata.enums.customer.CustomerEnum.IdCardType.CHINA_ID_CARD,
                "110101199001011234",
                "13800138000",
                true
        );

        // 初始化标的
        subject = new ProposalSubject(
                "subject-123",
                com.titanium.policy.common.enums.SubjectType.VEHICLE,
                "京A12345",
                com.titanium.metadata.enums.underwriting.UnderwritingEnum.RiskLevel.STANDARD
        );

        // 创建投保意向单草稿
        proposal = Proposal.createDraft(
                "proposal-123",
                "PROP-202601220001",
                com.titanium.metadata.enums.policy.PolicyForm.INDIVIDUAL,
                com.titanium.metadata.enums.product.ProductEnum.SalesChannel.ONLINE,
                basicInfo,
                "tenant-123"
        );
    }

    /**
     * 测试创建投保意向单草稿
     */
    @Test
    void testCreateDraft() {
        // 验证投保意向单基本信息
        assertEquals("proposal-123", proposal.getProposalId());
        assertEquals("PROP-202601220001", proposal.getProposalNo());
        assertEquals(com.titanium.metadata.enums.policy.PolicyForm.INDIVIDUAL, proposal.getPolicyForm());
        assertEquals(com.titanium.metadata.enums.product.ProductEnum.SalesChannel.ONLINE, proposal.getChannel());
        assertEquals(ProposalStatus.StatusCode.DRAFT, proposal.getStatus().statusCode());
        assertNotNull(proposal.getCreateTime());
        assertNotNull(proposal.getUpdateTime());
        assertEquals("tenant-123", proposal.getTenantId());
        assertEquals(basicInfo, proposal.getBasicInfo());
    }

    /**
     * 测试添加申请人
     */
    @Test
    void testAddApplicant() {
        // 添加申请人
        proposal.addApplicant(applicant);
        // 验证申请人是否添加成功
        assertEquals(1, proposal.getApplicants().size());
        assertEquals(applicant, proposal.getApplicants().get(0));
    }

    /**
     * 测试添加标的
     */
    @Test
    void testAddSubject() {
        // 添加标的
        proposal.addSubject(subject);
        // 验证标的是否添加成功
        assertEquals(1, proposal.getSubjects().size());
        assertEquals(subject, proposal.getSubjects().get(0));
    }

    /**
     * 测试提交投保意向单
     */
    @Test
    void testSubmitProposal() {
        // 添加申请人和标的
        proposal.addApplicant(applicant);
        proposal.addSubject(subject);
        // 提交投保意向单
        proposal.submitProposal("客户确认提交");
        // 验证状态是否更新为已提交
        assertEquals(ProposalStatus.StatusCode.SUBMITTED, proposal.getStatus().statusCode());
        assertEquals("客户确认提交", proposal.getStatus().changeReason());
    }

    /**
     * 测试提交投保意向单时缺少申请人
     */
    @Test
    void testSubmitProposalWithoutApplicant() {
        // 只添加标的，不添加申请人
        proposal.addSubject(subject);
        // 验证提交时是否抛出异常
        PolicyBusinessRuleException exception = assertThrows(PolicyBusinessRuleException.class, () -> {
            proposal.submitProposal("客户确认提交");
        });
        assertTrue(exception.getMessage().contains("At least one applicant is required"));
    }

    /**
     * 测试提交投保意向单时缺少标的
     */
    @Test
    void testSubmitProposalWithoutSubject() {
        // 只添加申请人，不添加标的
        proposal.addApplicant(applicant);
        // 验证提交时是否抛出异常
        PolicyBusinessRuleException exception = assertThrows(PolicyBusinessRuleException.class, () -> {
            proposal.submitProposal("客户确认提交");
        });
        assertTrue(exception.getMessage().contains("At least one subject is required"));
    }

    /**
     * 测试转为投保单
     */
    @Test
    void testConvertToApplication() {
        // 添加申请人和标的
        proposal.addApplicant(applicant);
        proposal.addSubject(subject);
        // 提交投保意向单
        proposal.submitProposal("客户确认提交");
        // 转为投保单
        proposal.convertToApplication("转为投保单");
        // 验证状态是否更新为已转投保单
        assertEquals(ProposalStatus.StatusCode.CONVERTED_TO_APPLICATION, proposal.getStatus().statusCode());
        assertEquals("转为投保单", proposal.getStatus().changeReason());
    }

    /**
     * 测试作废投保意向单
     */
    @Test
    void testVoidProposal() {
        // 添加申请人和标的
        proposal.addApplicant(applicant);
        proposal.addSubject(subject);
        // 提交投保意向单
        proposal.submitProposal("客户确认提交");
        // 作废投保意向单
        proposal.voidProposal("客户取消投保");
        // 验证状态是否更新为作废
        assertEquals(ProposalStatus.StatusCode.VOIDED, proposal.getStatus().statusCode());
        assertEquals("客户取消投保", proposal.getStatus().changeReason());
    }

    /**
     * 测试已转为投保单的投保意向单无法再次转为投保单
     */
    @Test
    void testConvertToApplicationAfterConversion() {
        // 添加申请人和标的
        proposal.addApplicant(applicant);
        proposal.addSubject(subject);
        // 提交投保意向单
        proposal.submitProposal("客户确认提交");
        // 第一次转为投保单
        proposal.convertToApplication("转为投保单");
        // 验证第二次转为投保单时是否抛出异常
        PolicyBusinessRuleException exception = assertThrows(PolicyBusinessRuleException.class, () -> {
            proposal.convertToApplication("再次转为投保单");
        });
        assertTrue(exception.getMessage().contains("Only submitted proposals can be converted to application"));
    }
}
