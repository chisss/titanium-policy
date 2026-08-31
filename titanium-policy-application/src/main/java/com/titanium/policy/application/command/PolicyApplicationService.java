package com.titanium.policy.application.command;


import java.math.BigDecimal;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.policy.application.orchestration.issuance.orchestrator.IssuanceOrchestrator;
import com.titanium.policy.command.ActivatePolicyCommand;
import com.titanium.policy.command.ApplyPolicyEndorsementCommand;
import com.titanium.policy.command.ApplyPolicyMaintenanceCommand;
import com.titanium.policy.command.CancelPolicyCommand;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.command.CreatePolicyDirectlyCommand;
import com.titanium.policy.command.DistributeDividendCommand;
import com.titanium.policy.command.IssuePolicyCommand;
import com.titanium.policy.command.MatureDuePolicyCommand;
import com.titanium.policy.command.MaturePolicyCommand;
import com.titanium.policy.command.PayAnnuityBenefitCommand;
import com.titanium.policy.command.RecordPremiumCollectionCommand;
import com.titanium.policy.command.ResumePolicyCommand;
import com.titanium.policy.command.StartAnnuityPayoutCommand;
import com.titanium.policy.command.SuspendPolicyCommand;
import com.titanium.policy.command.TerminatePolicyCommand;
import com.titanium.policy.command.UpdateAccountValueCommand;
import com.titanium.policy.command.WaivePremiumCommand;
import com.titanium.policy.valueobject.IssuanceProcessConfig;
import com.titanium.policy.valueobject.IssuanceRequest;
import com.titanium.policy.valueobject.IssuanceResult;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceApplicationReceipt;

import lombok.RequiredArgsConstructor;

/**
 * 保单应用服务
 */
@Service
@RequiredArgsConstructor
public class PolicyApplicationService {
    private final CommandGateway       commandGateway;

    private final IssuanceOrchestrator issuanceOrchestrator;

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

    /** 应用正式保全合同变更并返回聚合权威回执。 */
    public PolicyMaintenanceApplicationReceipt applyMaintenance(ApplyPolicyMaintenanceCommand command) {
        return commandGateway.sendAndWait(command);
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

    /** 回写跨域确认的保费收讫事实。 */
    public void recordPremiumCollection(RecordPremiumCollectionCommand command) {
        commandGateway.sendAndWait(command);
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

    // ==================== 寿险给付/给付期命令入口 ====================

    /**
     * 启动年金给付期（年金险专属）：保单进入给付期，按频率周期性给付生存年金，不终止保单。
     *
     * @param command 启动年金给付命令
     */
    public void startAnnuityPayout(StartAnnuityPayoutCommand command) {
        commandGateway.sendAndWait(command);
    }

    /**
     * 给付一期年金（给付期内定时触发）：逐期推进已给付期数，给满约定期数后计划完成，不改保单状态。
     *
     * @param command 给付年金命令
     */
    public void payAnnuityBenefit(PayAnnuityBenefitCommand command) {
        commandGateway.sendAndWait(command);
    }

    /**
     * 保单满期给付（两全险/生存给付型寿险）：给付满期生存保险金并使保单转为满期（EXPIRED，终态）。
     *
     * @param command 满期给付命令
     */
    public void maturePolicy(MaturePolicyCommand command) {
        commandGateway.sendAndWait(command);
    }

    /**
     * 保单到期满期给付（定时任务专用）：满期金额由聚合自身基本保额推导，无需调用方提供金额。
     *
     * @param command 到期满期给付命令
     */
    public void matureDuePolicy(MatureDuePolicyCommand command) {
        commandGateway.sendAndWait(command);
    }

    /**
     * 保费豁免（投保人身故/全残等豁免后续保费）：保单持续有效，仅标记后续保费免缴。
     *
     * @param command 保费豁免命令
     */
    public void waivePremium(WaivePremiumCommand command) {
        commandGateway.sendAndWait(command);
    }

    /**
     * 派发红利（分红险年度红利处理）：按红利领取方式处置，留存类累加累计红利，不改保单状态。
     *
     * @param command 红利派发命令
     */
    public void distributeDividend(DistributeDividendCommand command) {
        commandGateway.sendAndWait(command);
    }

    /**
     * 回写投资账户价值（投连/万能保单，investment 域账户价值变更后经 Feign 回写）。
     * <p>
     * 由应用层构造命令，表现层不依赖领域命令。账户价值为展示型最终一致数据，仅更新聚合内投资账户价值。
     * </p>
     *
     * @param policyId 保单ID
     * @param accountId 投资账户ID
     * @param accountValue 最新账户价值金额
     * @param currency 币种
     * @param tenantId 租户ID
     */
    public void updateAccountValue(String policyId, String accountId, BigDecimal accountValue, String currency,
                                   String tenantId) {
        commandGateway.sendAndWait(new UpdateAccountValueCommand(policyId, accountId, accountValue, currency, tenantId));
    }
}
