package com.titanium.policy.application;

import java.util.Optional;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.metadata.enums.PolicyEnum.PolicyStatus;
import com.titanium.policy.aggregate.Policy;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.repository.PolicyRepository;
import com.titanium.policy.service.PolicyService;

import jakarta.annotation.Resource;

/**
 * 保单应用服务
 */
@Service
public class PolicyApplicationService {
    @Resource
    private CommandGateway   commandGateway;

    @Resource
    private PolicyRepository policyRepository;

    @Resource
    private PolicyService    policyService;

    /**
     * 创建保单
     * 
     * @param command 创建保单命令
     */
    public String createPolicy(CreatePolicyCommand command) {
        return commandGateway.sendAndWait(command);
    }

    /**
     * 根据ID获取保单
     * 
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 保单对象
     */
    public Optional<Policy> getPolicyById(String policyId, String tenantId) {
        return policyRepository.findById(policyId, tenantId);
    }

    /**
     * 激活保单
     * 
     * @param policyId 保单ID
     * @param tenantId 租户ID
     */
    public void activatePolicy(String policyId, String tenantId) {
        // 这里可以添加更多的业务逻辑和验证
        Policy policy = policyRepository.findById(policyId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("保单不存在"));

        policy.activate();
    }

    /**
     * 审核保单
     * 
     * @param policyId 保单ID
     * @param tenantId 租户ID
     * @return 审核结果
     */
    public boolean approvePolicy(String policyId, String tenantId) {
        // 1. 获取保单
        Policy policy = policyRepository.findById(policyId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("保单不存在"));

        // 2. 使用领域服务验证保单数据完整性
        if (!policyService.validatePolicyData(policy)) {
            throw new IllegalArgumentException("保单数据不完整");
        }

        // 3. 使用领域服务计算保费
        policy = policyService.calculatePremium(policy);

        // 4. 使用领域服务检查保单是否可以激活
        if (policyService.canActivate(policy)) {
            // 5. 使用领域服务更新保单状态为已激活
            policyService.updatePolicyStatus(policy, PolicyStatus.EFFECTIVE);
            return true;
        } else {
            // 6. 如果不能激活，更新状态为审核失败
            policyService.updatePolicyStatus(policy, PolicyStatus.PENDING);
            return false;
        }
    }

    /**
     * 批量检查保单状态并更新
     * 
     * @param tenantId 租户ID
     */
    public void batchUpdatePolicyStatus(String tenantId) {
        // 获取所有待处理或已激活的保单
        Iterable<Policy> policies = policyRepository.findByStatusIn(tenantId, PolicyStatus.PENDING,
                PolicyStatus.EFFECTIVE);

        for (Policy policy : policies) {
            // 使用领域服务检查保单是否已过期
            if (policyService.isExpired(policy)) {
                // 使用领域服务更新保单状态为已过期
                policyService.updatePolicyStatus(policy, PolicyStatus.EXPIRED);
            }
        }
    }
}
