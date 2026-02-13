package com.titanium.policy.application.command;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.policy.command.ActivatePolicyCommand;
import com.titanium.policy.command.CancelPolicyCommand;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.command.CreatePolicyDirectlyCommand;
import com.titanium.policy.command.IssuePolicyCommand;
import com.titanium.policy.command.ResumePolicyCommand;
import com.titanium.policy.command.SuspendPolicyCommand;
import com.titanium.policy.command.TerminatePolicyCommand;
import com.titanium.policy.service.IssuanceOrchestrator;
import com.titanium.policy.service.IssuanceRequest;
import com.titanium.policy.service.IssuanceResult;
import com.titanium.policy.valueobject.IssuanceProcessConfig;

import jakarta.annotation.Resource;

/**
 * 保单应用服务
 */
@Service
public class PolicyApplicationService {
    @Resource
    private CommandGateway       commandGateway;

    @Resource
    private IssuanceOrchestrator issuanceOrchestrator;

    /**
     * 创建保单（从投保单创建）
     */
    public String createPolicy(CreatePolicyCommand command) {
        commandGateway.sendAndWait(command);
        return command.policyId();
    }

    /**
     * 智能出单（根据出单配置编排）
     */
    public IssuanceResult issueByConfig(IssuanceProcessConfig config, IssuanceRequest request) {
        return issuanceOrchestrator.orchestrate(config, request);
    }

    /**
     * 一步出单
     */
    public String createPolicyDirectly(CreatePolicyDirectlyCommand command) {
        commandGateway.sendAndWait(command);
        return command.policyId();
    }

    /**
     * 签发保单
     */
    public void issuePolicy(String policyId, String operatorId, String tenantId) {
        commandGateway.sendAndWait(new IssuePolicyCommand(policyId, operatorId, tenantId));
    }

    /**
     * 激活保单
     */
    public void activatePolicy(String policyId, String tenantId) {
        commandGateway.sendAndWait(new ActivatePolicyCommand(policyId, tenantId));
    }

    /**
     * 暂停保单（保全域触发）
     */
    public void suspendPolicy(SuspendPolicyCommand command) {
        commandGateway.sendAndWait(command);
    }

    /**
     * 恢复保单（保全域触发）
     */
    public void resumePolicy(ResumePolicyCommand command) {
        commandGateway.sendAndWait(command);
    }

    /**
     * 终止保单（保全域触发/退保）
     */
    public void terminatePolicy(TerminatePolicyCommand command) {
        commandGateway.sendAndWait(command);
    }

    /**
     * 取消保单（仅未生效保单）
     */
    public void cancelPolicy(CancelPolicyCommand command) {
        commandGateway.sendAndWait(command);
    }
}
