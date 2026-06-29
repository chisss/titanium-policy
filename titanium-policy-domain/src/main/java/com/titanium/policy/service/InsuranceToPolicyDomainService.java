package com.titanium.policy.service;

import java.util.UUID;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.stereotype.Service;

import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.aggregate.Insurance;
import com.titanium.policy.command.CreatePolicyCommand;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 投保单转保单领域服务
 * <p>
 * 核保通过后，将投保单数据转化为正式保单。
 * 负责从 Insurance 聚合中提取数据构建 CreatePolicyCommand。
 * </p>
 */
@Slf4j
@Service
public class InsuranceToPolicyDomainService {

    @Resource
    private CommandGateway    commandGateway;

    @Resource
    private PolicyNoGenerator policyNoGenerator;

    /**
     * 从投保单创建保单
     *
     * @param insurance 投保单聚合
     * @return 保单ID
     */
    public String createPolicyFromInsurance(Insurance insurance) {
        String policyId = UUID.randomUUID().toString();
        String policyNo = policyNoGenerator.generatePolicyNo();

        CreatePolicyCommand command = new CreatePolicyCommand(
                policyId,
                policyNo,
                insurance.getInsuranceId(),
                insurance.getPolicyForm(),
                null, // issueOrg
                insurance.getBasicInfo().holderId(),
                null, // insuredId - 从参与方清单取
                insurance.getBasicInfo().exactPremium() != null
                        ? insurance.getBasicInfo().exactPremium()
                        : Money.zero("CNY"),
                insurance.getBasicInfo().exactPremium() != null
                        ? insurance.getBasicInfo().exactPremium()
                        : Money.zero("CNY"),
                insurance.getBasicInfo().insurancePeriodStart(),
                insurance.getBasicInfo().insurancePeriodEnd(),
                null, // channel
                insurance.getTenantId()
        );

        commandGateway.sendAndWait(command);
        log.info("从投保单创建保单完成, insuranceId={}, policyId={}, policyNo={}",
                insurance.getInsuranceId(), policyId, policyNo);

        return policyId;
    }
}
