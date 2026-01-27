package com.titanium.policy.application.command;

import java.util.Optional;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.policy.aggregate.Policy;
import com.titanium.policy.command.ActivatePolicyCommand;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.repository.PolicyRepository;

import jakarta.annotation.Resource;

/**
 * 保单应用服务
 * <p>
 * 处理保单相关的命令，协调领域层和基础设施层
 * </p>
 */
@Service
public class PolicyApplicationService {
    @Resource
    private CommandGateway   commandGateway;

    @Resource
    private PolicyRepository policyRepository;

    /**
     * 创建保单
     *
     * @param command 创建保单命令
     * @return 保单ID
     */
    public String createPolicy(CreatePolicyCommand command) {
        // 发送命令
        commandGateway.sendAndWait(command);
        return command.policyId();
    }

    /**
     * 激活保单
     *
     * @param policyId 保单ID
     * @param tenantId 租户ID
     */
    public void activatePolicy(String policyId, String tenantId) {
        // 从仓库获取保单
        Optional<Policy> policyOptional = policyRepository.findById(policyId, tenantId);
        if (policyOptional.isEmpty()) {
            throw new IllegalArgumentException("Policy not found: " + policyId);
        }
        ActivatePolicyCommand command = new ActivatePolicyCommand(policyId, tenantId);
        commandGateway.sendAndWait(command);
    }
}
