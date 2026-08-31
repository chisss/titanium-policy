package com.titanium.policy.application.orchestration.issuance.assembler;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Component;

import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.command.CreatePolicyDirectlyCommand;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.valueobject.IssuanceRequest;
import com.titanium.policy.valueobject.policy.ChannelInfo;
import com.titanium.policy.valueobject.policy.CollectionInfo;
import com.titanium.policy.valueobject.policy.PolicyPeriod;

/**
 * 一步出单直接创建保单命令装配器。
 * <p>
 * 把出单请求、装配完成的险种段与解析后的产品配置组装为 {@link CreatePolicyDirectlyCommand}，
 * 补齐参与方清单、保单期间、收费信息与渠道信息（此前命令缺参与方字段，一步出单产出的保单
 * 查不到投保人/被保险人/受益人）。
 * </p>
 */
@Component
public class CreatePolicyDirectlyCommandAssembler {

    /**
     * 装配一步出单命令。
     *
     * @param request      出单请求
     * @param policyId     保单ID
     * @param policyNo     保单号
     * @param lines        险种段列表（由 {@code PolicyProductAssembler} 装配）
     * @param totalPremium 保单总保费（= Σ 计入段的保费）
     * @param policyForm   保单形态（由编排器经产品配置解析）
     * @param policyPeriod 保单期间（由编排器经产品配置解析）
     * @return 创建保单命令
     */
    public CreatePolicyDirectlyCommand assemble(IssuanceRequest request, String policyId, String policyNo,
                                                List<PolicyProduct> lines, Money totalPremium, PolicyForm policyForm,
                                                PolicyPeriod policyPeriod) {
        return new CreatePolicyDirectlyCommand(policyId, policyNo, request.bizNo(), request.marketPackageId(),
                policyForm, request.mainProductId(), request.insuredPartyList(), lines, request.mainSumInsured(),
                totalPremium, policyPeriod, null,
                CollectionInfo.initial(request.collectionMode(), totalPremium, LocalDateTime.now()),
                buildChannelInfo(request), request.insuranceType(), request.tenantId());
    }

    /**
     * 渠道信息（此前命令有 channel 字段但发事件时被丢弃，保单查不到来源渠道）。
     */
    private ChannelInfo buildChannelInfo(IssuanceRequest request) {
        if (request.channelId() == null && request.salesChannel() == null && request.agentId() == null) {
            return null;
        }
        return new ChannelInfo(request.channelId(), null, request.salesChannel(), request.agentId(), null);
    }
}
