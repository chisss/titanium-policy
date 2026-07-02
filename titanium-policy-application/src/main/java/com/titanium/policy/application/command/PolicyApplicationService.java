package com.titanium.policy.application.command;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.api.dto.AmountDTO;
import com.titanium.policy.api.dto.CreatePolicyDTO;
import com.titanium.policy.application.orchestration.IssuanceOrchestrator;
import com.titanium.policy.command.ActivatePolicyCommand;
import com.titanium.policy.command.ApplyPolicyEndorsementCommand;
import com.titanium.policy.command.CancelPolicyCommand;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.command.CreatePolicyDirectlyCommand;
import com.titanium.policy.command.IssuePolicyCommand;
import com.titanium.policy.command.ResumePolicyCommand;
import com.titanium.policy.command.SuspendPolicyCommand;
import com.titanium.policy.command.TerminatePolicyCommand;
import com.titanium.policy.valueobject.IssuanceProcessConfig;
import com.titanium.policy.valueobject.IssuanceRequest;
import com.titanium.policy.valueobject.IssuanceResult;

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
     * 创建保单（Web 入口重载：由应用层据 API DTO 构造命令，表现层不依赖领域命令）
     * <p>
     * DTO 未承载的字段（投保单/形态/机构/保额/被保险人/渠道）暂置 null，与既有行为一致。
     * </p>
     *
     * @param dto 创建保单 DTO
     * @return 保单ID
     */
    public String createPolicy(CreatePolicyDTO dto) {
        CreatePolicyCommand command = new CreatePolicyCommand(dto.getPolicyId(), dto.getPolicyNumber(), null, null, null,
                dto.getCustomerId(), null, null, toMoney(dto.getPremium()), dto.getEffectiveDate(), dto.getExpiryDate(),
                null, dto.getTenantId());
        commandGateway.sendAndWait(command);
        return dto.getPolicyId();
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
     * 一步出单（Web 入口重载：由应用层据 API DTO 构造命令，表现层不依赖领域命令）
     *
     * @param dto 创建保单 DTO
     * @return 保单ID
     */
    public String createPolicyDirectly(CreatePolicyDTO dto) {
        CreatePolicyDirectlyCommand command = new CreatePolicyDirectlyCommand(dto.getPolicyId(), dto.getPolicyNumber(),
                dto.getProductId(), null, null, dto.getCustomerId(), 0, null, dto.getEffectiveDate(),
                dto.getExpiryDate(), null, dto.getTenantId());
        commandGateway.sendAndWait(command);
        return dto.getPolicyId();
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
     * 暂停保单（Web 入口重载：由应用层构造命令，表现层不依赖领域命令）
     *
     * @param policyId 保单ID
     * @param reason 暂停原因
     * @param operatorId 操作人ID
     * @param tenantId 租户ID
     */
    public void suspendPolicy(String policyId, String reason, String operatorId, String tenantId) {
        commandGateway.sendAndWait(new SuspendPolicyCommand(policyId, reason, operatorId, tenantId));
    }

    /**
     * 恢复保单（保全域触发）
     */
    public void resumePolicy(ResumePolicyCommand command) {
        commandGateway.sendAndWait(command);
    }

    /**
     * 恢复保单（Web 入口重载：由应用层构造命令，表现层不依赖领域命令）
     *
     * @param policyId 保单ID
     * @param reason 恢复原因
     * @param operatorId 操作人ID
     * @param tenantId 租户ID
     */
    public void resumePolicy(String policyId, String reason, String operatorId, String tenantId) {
        commandGateway.sendAndWait(new ResumePolicyCommand(policyId, reason, operatorId, tenantId));
    }

    /**
     * 终止保单（保全域触发/退保）
     */
    public void terminatePolicy(TerminatePolicyCommand command) {
        commandGateway.sendAndWait(command);
    }

    /**
     * 终止保单（Web 入口重载：由应用层构造命令，表现层不依赖领域命令）
     *
     * @param policyId 保单ID
     * @param reason 终止原因
     * @param operatorId 操作人ID
     * @param terminationReason 终止原因分类
     * @param tenantId 租户ID
     */
    public void terminatePolicy(String policyId, String reason, String operatorId,
                                PolicyEnum.TerminationReason terminationReason, String tenantId) {
        commandGateway.sendAndWait(new TerminatePolicyCommand(policyId, reason, operatorId, terminationReason,
                tenantId));
    }

    /**
     * 取消保单（仅未生效保单）
     */
    public void cancelPolicy(CancelPolicyCommand command) {
        commandGateway.sendAndWait(command);
    }

    /**
     * 取消保单（Web 入口重载：由应用层构造命令，表现层不依赖领域命令）
     *
     * @param policyId 保单ID
     * @param reason 取消原因
     * @param operatorId 操作人ID
     * @param tenantId 租户ID
     */
    public void cancelPolicy(String policyId, String reason, String operatorId, String tenantId) {
        commandGateway.sendAndWait(new CancelPolicyCommand(policyId, reason, operatorId, tenantId));
    }

    /**
     * API 金额 DTO → Money 值对象（金额为空时返回 null）
     */
    private Money toMoney(AmountDTO amount) {
        return amount != null && amount.getValue() != null
                ? Money.of(amount.getValue(), amount.getCurrency() != null ? amount.getCurrency() : "CNY")
                : null;
    }
}
