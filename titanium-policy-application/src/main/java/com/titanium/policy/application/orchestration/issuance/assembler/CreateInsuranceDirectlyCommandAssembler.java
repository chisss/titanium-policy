package com.titanium.policy.application.orchestration.issuance.assembler;

import java.util.List;

import org.springframework.stereotype.Component;

import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.policy.command.CreateInsuranceDirectlyCommand;
import com.titanium.policy.entity.insurance.InsuranceLine;
import com.titanium.policy.valueobject.IssuancePlanLine;
import com.titanium.policy.valueobject.IssuanceRequest;
import com.titanium.policy.valueobject.policy.ChannelInfo;

/**
 * 投保单命令装配器（两步出单起点）。
 * <p>
 * 把出单请求与装配完成的险种段组装为 {@link CreateInsuranceDirectlyCommand}，贯通保额与缴费
 * 条件（此前向命令传 null 的 sumInsured/paymentMode 使 Saga 的真实保费计算被静默跳过）。
 * </p>
 */
@Component
public class CreateInsuranceDirectlyCommandAssembler {

    /** 核保优先级缺省值（两步出单起点无加急诉求） */
    private static final int DEFAULT_UNDERWRITING_PRIORITY = 0;

    /**
     * 装配创建投保单命令。
     *
     * @param request    出单请求
     * @param insuranceId 投保单ID
     * @param insuranceNo 投保单编号
     * @param lines      险种段列表（由 {@code InsuranceLineAssembler} 装配）
     * @param policyForm 保单形态（由编排器经产品配置解析）
     * @param mainPlan   主险方案行（可空，缴费条件取其声明值）
     * @return 创建投保单命令
     */
    public CreateInsuranceDirectlyCommand assemble(IssuanceRequest request, String insuranceId, String insuranceNo,
                                                   List<InsuranceLine> lines, PolicyForm policyForm,
                                                   IssuancePlanLine mainPlan) {
        return new CreateInsuranceDirectlyCommand(insuranceId, insuranceNo, policyForm, request.holderCustomerId(),
                request.insuredCount(),
                request.quotedPremium() != null ? request.quotedPremium().value() : null, request.periodStart(),
                request.periodEnd(), lines, DEFAULT_UNDERWRITING_PRIORITY, request.insuredPartyList(),
                request.insuranceType(), request.collectionMode(), buildChannelInfo(request), request.bizNo(),
                request.marketPackageId(), request.tenantId(),
                request.mainSumInsured() != null ? request.mainSumInsured().value() : null,
                mainPlan != null && mainPlan.paymentFrequency() != null ? mainPlan.paymentFrequency().getCode() : null,
                mainPlan != null && mainPlan.premiumPaymentYears() != null ? mainPlan.premiumPaymentYears() : 0);
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
