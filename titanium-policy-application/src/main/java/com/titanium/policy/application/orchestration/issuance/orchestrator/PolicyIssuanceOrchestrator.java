package com.titanium.policy.application.orchestration.issuance.orchestrator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.aggregate.Insurance;
import com.titanium.policy.application.orchestration.issuance.assembler.PolicyProductAssembler;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.generator.PolicyNoGenerator;
import com.titanium.policy.service.PolicyIssuanceDomainService;
import com.titanium.policy.valueobject.insurance.PolicyIssuanceDecision;
import com.titanium.policy.valueobject.insurance.UnderwritingResult;
import com.titanium.policy.valueobject.policy.CollectionInfo;
import com.titanium.policy.valueobject.policy.PolicyPeriod;

/**
 * 承保出单编排器
 * <p>
 * 应用层编排样板：演示「编排」与「领域规则」的职责切分。本类<b>只做协调</b>——
 * 生成技术标识、调度领域服务拿承保决策、据决策发命令，<b>不写任何承保业务判断</b>。
 * </p>
 * <p>
 * 对照领域服务 {@link PolicyIssuanceDomainService}：核保结论准入、保单要素推导等业务规则
 * 全部内聚在领域服务；本编排器不出现「核保是否通过」「保费怎么取」的 if 分支，从而避免业务逻辑
 * 散落到应用层导致的贫血。
 * </p>
 * <p>
 * 依赖注入采用<b>构造器注入</b>（符合规约「构造器注入优先」）。注意：领域服务、生成器、CommandGateway
 * 均可由应用层注入调用；但 {@code domain.port} 只应由应用层与基础设施持有——本类若需外部数据，
 * 也应通过注入 Port 获取，而领域服务内部则绝不碰 Port。
 * </p>
 */
@Service
public class PolicyIssuanceOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(PolicyIssuanceOrchestrator.class);

    private final CommandGateway commandGateway;
    private final PolicyNoGenerator policyNoGenerator;
    private final PolicyIssuanceDomainService policyIssuanceDomainService;

    private final PolicyProductAssembler      policyProductAssembler;

    /**
     * 构造器注入（禁用字段注入）
     *
     * @param commandGateway Axon 命令网关
     * @param policyNoGenerator 保单号生成器
     * @param policyIssuanceDomainService 承保领域服务
     * @param policyProductAssembler 保单险种段装配器（冻结条款与责任快照）
     */
    public PolicyIssuanceOrchestrator(CommandGateway commandGateway, PolicyNoGenerator policyNoGenerator,
                                      PolicyIssuanceDomainService policyIssuanceDomainService,
                                      PolicyProductAssembler policyProductAssembler) {
        this.commandGateway = commandGateway;
        this.policyNoGenerator = policyNoGenerator;
        this.policyIssuanceDomainService = policyIssuanceDomainService;
        this.policyProductAssembler = policyProductAssembler;
    }

    /**
     * 承保出单：核保通过后据投保单与核保结果创建正式保单
     * <p>
     * 编排骨架仅三步：①生成保单号（技术标识）；②调领域服务拿承保决策（业务规则外包）；
     * ③据决策发命令或终止。业务判断全在第②步的领域服务内。
     * </p>
     *
     * @param insurance 投保单聚合
     * @param underwritingResult 核保结果
     * @return 已创建保单的 policyId；不可承保时返回 null
     */
    public String issue(Insurance insurance, UnderwritingResult underwritingResult) {
        // ① 领域服务裁决：能否承保 + 保单构建要素（纯业务规则，无 I/O）
        PolicyIssuanceDecision decision = policyIssuanceDomainService.decideIssuance(insurance, underwritingResult);
        if (!decision.acceptable()) {
            LOG.warn("[承保编排] 不可承保, insuranceId={}, 原因={}", insurance.getInsuranceId(), decision.rejectReason());
            return null;
        }

        // ② 生成技术性标识（编排职责）
        String policyId = UUID.randomUUID().toString();
        String policyNo = policyNoGenerator.generatePolicyNo();

        // ③ 装配承保段：把投保段精化为保单段，冻结条款与责任快照（取数职责）
        List<PolicyProduct> policyProducts = policyProductAssembler
                .assembleFromInsuranceLines(insurance.getInsuranceLines(), insurance.getTenantId());
        Money totalPremium = policyProductAssembler.sumPremium(policyProducts);
        if (totalPremium == null) {
            totalPremium = decision.premium();
        }

        // ④ 据领域决策组装并下发命令（编排职责，不含业务判断）
        CreatePolicyCommand command = new CreatePolicyCommand(policyId, policyNo, insurance.getInsuranceId(),
                insurance.getProposalId(),
                insurance.getUnderwritingResult() != null ? insurance.getUnderwritingResult().underwritingId() : null,
                insurance.getMarketPackageId(), decision.policyForm(), mainProductId(insurance), null,
                insurance.getInsuredPartyList(), policyProducts, mainSumInsured(insurance), totalPremium,
                PolicyPeriod.of(decision.insurancePeriodStart(), decision.insurancePeriodEnd(), 0, 0), null,
                CollectionInfo.initial(insurance.getCollectionMode(), totalPremium, LocalDateTime.now()),
                insurance.getChannelInfo(), insurance.getInsuranceType(), insurance.getTenantId());
        commandGateway.sendAndWait(command);

        LOG.info("[承保编排] 承保出单完成, insuranceId={}, policyId={}, policyNo={}, 险种段数={}",
                insurance.getInsuranceId(), policyId, policyNo, policyProducts.size());
        return policyId;
    }

    /**
     * 主险产品ID（读侧便捷冗余，真相在险种段上）。
     */
    private String mainProductId(Insurance insurance) {
        return insurance.mainLine() != null ? insurance.mainLine().productId() : null;
    }

    /**
     * 主险保额（读侧便捷冗余，各段保额在段上）。
     */
    private Money mainSumInsured(Insurance insurance) {
        return insurance.mainLine() != null ? insurance.mainLine().sumInsured() : null;
    }
}
