package com.titanium.policy.application.command;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.SalesChannel;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.command.CreateProposalCommand;
import com.titanium.policy.command.SubmitProposalCommand;
import com.titanium.policy.command.VoidProposalCommand;

import jakarta.annotation.Resource;

/**
 * 投保意向单应用服务
 */
@Service
public class ProposalApplicationService {
    @Resource
    private CommandGateway commandGateway;

    /**
     * 创建投保意向单
     */
    public String createProposal(CreateProposalCommand command) {
        commandGateway.sendAndWait(command);
        return command.proposalId();
    }

    /**
     * 创建投保意向单（Web 入口重载：由应用层构造命令，表现层不依赖领域命令）
     *
     * @param proposalId 意向单ID
     * @param proposalNo 意向单编号
     * @param policyForm 保单形态
     * @param channel 销售渠道
     * @param customerId 客户ID
     * @param intendedSumInsured 意向保额
     * @param intendedPremium 意向保费
     * @param currency 币种
     * @param insurancePeriodStart 保险起期
     * @param insurancePeriodEnd 保险止期
     * @param expectedProductCode 期望险种编码
     * @param insuranceType 险种三级分类（可空）
     * @param tenantId 租户ID
     * @return 意向单ID
     */
    public String createProposal(String proposalId, String proposalNo, PolicyForm policyForm, SalesChannel channel,
                                 String customerId, BigDecimal intendedSumInsured, BigDecimal intendedPremium,
                                 String currency, LocalDateTime insurancePeriodStart, LocalDateTime insurancePeriodEnd,
                                 String expectedProductCode, InsuranceProductType insuranceType, String tenantId) {
        String resolvedCurrency = currency != null ? currency : "CNY";
        CreateProposalCommand command = new CreateProposalCommand(proposalId, proposalNo, policyForm, channel,
                customerId, toMoney(intendedSumInsured, resolvedCurrency), toMoney(intendedPremium, resolvedCurrency),
                insurancePeriodStart, insurancePeriodEnd, expectedProductCode, insuranceType, tenantId);
        commandGateway.sendAndWait(command);
        return proposalId;
    }

    /**
     * 提交投保意向单
     */
    public void submitProposal(SubmitProposalCommand command) {
        commandGateway.sendAndWait(command);
    }

    /**
     * 提交投保意向单（Web 入口重载：由应用层构造命令，表现层不依赖领域命令）
     *
     * @param proposalId 意向单ID
     * @param changeReason 变更原因
     * @param tenantId 租户ID
     */
    public void submitProposal(String proposalId, String changeReason, String tenantId) {
        commandGateway.sendAndWait(new SubmitProposalCommand(proposalId, changeReason, tenantId));
    }

    /**
     * 作废投保意向单
     */
    public void voidProposal(VoidProposalCommand command) {
        commandGateway.sendAndWait(command);
    }

    /**
     * BigDecimal + 币种 → Money 值对象（金额为空时返回 null）
     */
    private Money toMoney(BigDecimal value, String currency) {
        return value != null ? Money.of(value, currency) : null;
    }
}
