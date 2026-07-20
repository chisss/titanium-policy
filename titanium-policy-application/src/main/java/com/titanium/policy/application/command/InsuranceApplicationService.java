package com.titanium.policy.application.command;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.command.ConvertProposalToInsuranceCommand;
import com.titanium.policy.command.CreateInsuranceDirectlyCommand;
import com.titanium.policy.command.ReceiveUnderwritingResultCommand;
import com.titanium.policy.command.SubmitUnderwritingCommand;
import com.titanium.policy.command.TriggerIssuanceCommand;
import com.titanium.policy.valueobject.insurance.UnderwritingResult;

import jakarta.annotation.Resource;

/**
 * 投保单应用服务
 */
@Service
public class InsuranceApplicationService {
    @Resource
    private CommandGateway commandGateway;

    /**
     * 从投保意向单创建投保单（三步出单）
     */
    public String convertFromProposal(ConvertProposalToInsuranceCommand command) {
        commandGateway.sendAndWait(command);
        return command.insuranceId();
    }

    /**
     * 从投保意向单创建投保单（标量重载：由应用层构造命令，供内部编排复用）
     *
     * @param insuranceId 投保单ID
     * @param insuranceNo 投保单编号
     * @param proposalId 关联意向单ID
     * @param policyForm 保单形态
     * @param applicantId 投保人ID
     * @param insuredCount 被保险人数
     * @param exactPremium 精确保费
     * @param currency 币种
     * @param insurancePeriodStart 保险起期
     * @param insurancePeriodEnd 保险止期
     * @param productCodes 险种编码列表
     * @param underwritingPriority 核保优先级
     * @param changeReason 转换原因
     * @param insuranceType 险种三级分类（可空）
     * @param tenantId 租户ID
     * @return 投保单ID
     */
    public String convertFromProposal(String insuranceId, String insuranceNo, String proposalId, PolicyForm policyForm,
                                      String applicantId, int insuredCount, BigDecimal exactPremium, String currency,
                                      LocalDateTime insurancePeriodStart, LocalDateTime insurancePeriodEnd,
                                      List<String> productCodes, int underwritingPriority, String changeReason,
                                      InsuranceProductType insuranceType, String tenantId) {
        Money premium = exactPremium != null ? Money.of(exactPremium, currency != null ? currency : "CNY") : null;
        // 标量重载无参与方清单上下文，传 null（Web 层调用方可改用命令重载传递完整清单）
        ConvertProposalToInsuranceCommand command = new ConvertProposalToInsuranceCommand(insuranceId, insuranceNo,
                proposalId, policyForm, applicantId, insuredCount, premium, insurancePeriodStart, insurancePeriodEnd,
                productCodes, underwritingPriority, changeReason, null, insuranceType, tenantId,
                null, null, 0);
        commandGateway.sendAndWait(command);
        return insuranceId;
    }

    /**
     * 直接创建投保单（两步出单）
     */
    public String createInsuranceDirectly(CreateInsuranceDirectlyCommand command) {
        commandGateway.sendAndWait(command);
        return command.insuranceId();
    }

    /**
     * 提交核保
     */
    public void submitUnderwriting(String insuranceId, String tenantId) {
        commandGateway.sendAndWait(new SubmitUnderwritingCommand(insuranceId, tenantId));
    }

    /**
     * 接收核保结果（核保域回调）
     */
    public void receiveUnderwritingResult(String insuranceId, UnderwritingResult result, String tenantId) {
        commandGateway.sendAndWait(new ReceiveUnderwritingResultCommand(insuranceId, result, tenantId));
    }

    /**
     * 触发承保出单
     */
    public void triggerIssuance(String insuranceId, String tenantId) {
        commandGateway.sendAndWait(new TriggerIssuanceCommand(insuranceId, tenantId));
    }
}
