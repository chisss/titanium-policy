package com.titanium.policy.application.command;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

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
