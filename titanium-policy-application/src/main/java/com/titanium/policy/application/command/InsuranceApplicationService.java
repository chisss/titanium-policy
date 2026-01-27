package com.titanium.policy.application.command;

import java.util.Optional;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.policy.aggregate.Insurance;
import com.titanium.policy.command.ConvertProposalToInsuranceCommand;
import com.titanium.policy.repository.InsuranceRepository;

import jakarta.annotation.Resource;

/**
 * 投保单应用服务
 * <p>
 * 处理投保单相关的命令，协调领域层和基础设施层
 * </p>
 */
@Service
public class InsuranceApplicationService {
    @Resource
    private CommandGateway      commandGateway;

    @Resource
    private InsuranceRepository insuranceRepository;

    /**
     * 从投保意向单创建投保单
     *
     * @param command 转换命令
     * @return 投保单ID
     */
    public String convertFromProposal(ConvertProposalToInsuranceCommand command) {
        // 发送命令
        commandGateway.sendAndWait(command);
        return command.insuranceId();
    }

    /**
     * 提交核保
     *
     * @param applicationId 投保单ID
     * @param tenantId 租户ID
     */
    public void submitUnderwriting(String applicationId, String tenantId) {
        // 从仓库获取投保单
        Optional<Insurance> insuranceOptional = insuranceRepository.findById(applicationId, tenantId);
        if (insuranceOptional.isEmpty()) {
            throw new IllegalArgumentException("Insurance application not found: " + applicationId);
        }
        Insurance insurance = insuranceOptional.get();
        // 提交核保
        insurance.submitUnderwriting();
        insuranceRepository.save(insurance);
    }

    /**
     * 触发承保流程
     *
     * @param insuranceId 投保单ID
     * @param tenantId 租户ID
     */
    public void triggerUnderwriting(String insuranceId, String tenantId) {
        // 从仓库获取投保单
        Optional<Insurance> insuranceOptional = insuranceRepository.findById(insuranceId, tenantId);
        if (insuranceOptional.isEmpty()) {
            throw new IllegalArgumentException("Insurance application not found: " + insuranceId);
        }
        Insurance insurance = insuranceOptional.get();
        // 触发承保流程
        insurance.triggerUnderwriting();
        insuranceRepository.save(insurance);
    }
}
