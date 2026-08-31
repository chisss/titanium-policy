package com.titanium.policy.application.orchestration.issuance.assembler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.policy.command.CreateProposalCommand;
import com.titanium.policy.entity.proposal.ProposalLine;
import com.titanium.policy.entity.proposal.ProposalSubject;
import com.titanium.policy.valueobject.IssuancePlanLine;
import com.titanium.policy.valueobject.IssuanceRequest;
import com.titanium.policy.valueobject.policy.ChannelInfo;

/**
 * 意向单命令装配器（三步出单起点）。
 * <p>
 * 把出单请求与生成的单据号装配为 {@link CreateProposalCommand}：将方案行中的标的意图压缩为意向
 * 阶段摘要（三步转换时再恢复物类标的）、推导主险意向产品与缴费条件、组装渠道信息。
 * </p>
 */
@Component
public class CreateProposalCommandAssembler {

    /**
     * 装配创建意向单命令。
     *
     * @param request       出单请求
     * @param proposalId    意向单ID
     * @param proposalNo    意向单编号
     * @param proposalLines 意向段列表（由 {@code ProposalLineAssembler} 装配）
     * @param policyForm    保单形态（由编排器经产品配置解析）
     * @return 创建意向单命令
     */
    public CreateProposalCommand assemble(IssuanceRequest request, String proposalId, String proposalNo,
                                          List<ProposalLine> proposalLines, PolicyForm policyForm) {
        IssuancePlanLine mainPlan = request.mainLine();
        ProposalLine mainProposalLine = proposalLines.stream().filter(ProposalLine::isMain).findFirst().orElse(null);
        return CreateProposalCommand.builder().proposalId(proposalId).proposalNo(proposalNo)
                .policyForm(policyForm).channel(request.salesChannel())
                .customerId(request.holderCustomerId()).intendedSumInsured(request.mainSumInsured())
                .intendedPremium(request.quotedPremium()).insurancePeriodStart(request.periodStart())
                .insurancePeriodEnd(request.periodEnd())
                .expectedProductCode(mainProposalLine != null ? mainProposalLine.productCode() : null)
                .proposalLines(proposalLines).proposalSubjects(buildProposalSubjects(request))
                .insuranceType(ProposalLine.resolveInsuranceType(request.insuranceType(), proposalLines))
                .bizNo(request.bizNo()).marketPackageId(request.marketPackageId()).tenantId(request.tenantId())
                .insuredPartyList(request.insuredPartyList()).collectionMode(request.collectionMode())
                .channelInfo(buildChannelInfo(request))
                .paymentMode(mainPlan != null && mainPlan.paymentFrequency() != null
                        ? mainPlan.paymentFrequency().getCode() : null)
                .premiumPaymentYears(mainPlan != null && mainPlan.premiumPaymentYears() != null
                        ? mainPlan.premiumPaymentYears() : 0)
                .build();
    }

    /** 将方案行中的标的意图压缩为意向阶段摘要，并在三步转换时恢复物类标的。 */
    private List<ProposalSubject> buildProposalSubjects(IssuanceRequest request) {
        List<ProposalSubject> subjects = new ArrayList<>();
        for (IssuancePlanLine line : request.planLines()) {
            if (line.subjects() == null) {
                continue;
            }
            for (IssuancePlanLine.SubjectIntent intent : line.subjects()) {
                subjects.add(new ProposalSubject(UUID.randomUUID().toString(), intent.subjectType(),
                        intent.subjectName(), null, intent.attributes() != null ? intent.attributes() : Map.of()));
            }
        }
        return List.copyOf(subjects);
    }

    /**
     * 渠道信息（此前命令有 channel 字段但发事件时被丢弃，保单查不到来源渠道）。
     */
    private ChannelInfo buildChannelInfo(IssuanceRequest request) {
        if (request.channelId() == null && request.salesChannel() == null && request.agentId() == null) {
            return null;
        }
        return new ChannelInfo(request.channelId(), null, request.salesChannel(), request.agentId(), null);
    }
}
