package com.titanium.policy.application.command;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.policy.command.ActivatePolicyCommand;
import com.titanium.policy.command.ApplyPolicyEndorsementCommand;
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
     * 产品驱动智能出单：出单模式由产品域配置决定，调用方无需指定步数。
     *
     * @param request 出单请求
     * @return 出单结果
     */
    public IssuanceResult issue(IssuanceRequest request) {
        return issuanceOrchestrator.orchestrate(request);
    }

    /**
     * 应用保单批改（数据/要素类批改回写编排）
     *
     * @param command 批改命令
     * @return 保单ID
     */
    public String applyEndorsement(ApplyPolicyEndorsementCommand command) {
        commandGateway.sendAndWait(command);
        return command.policyId();
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
