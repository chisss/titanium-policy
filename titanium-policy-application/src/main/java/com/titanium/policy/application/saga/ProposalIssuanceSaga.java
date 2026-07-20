package com.titanium.policy.application.saga;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.SagaLifecycle;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.springframework.beans.factory.annotation.Autowired;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.command.ConvertProposalCommand;
import com.titanium.policy.command.ConvertProposalToInsuranceCommand;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.event.proposal.ProposalConvertedEvent;
import com.titanium.policy.event.proposal.ProposalCreatedEvent;
import com.titanium.policy.event.proposal.ProposalSubmittedEvent;
import com.titanium.policy.event.proposal.ProposalVoidedEvent;
import com.titanium.policy.generator.PolicyNoGenerator;

import lombok.extern.slf4j.Slf4j;

/**
 * 意向单出单 Saga（意向单提交 -> 转投保单 -> 创建投保单 自动接力）
 * <p>
 * 以 {@code proposalId} 为关联键，消除三步出单中 Proposal SUBMITTED 状态停滞的断点：
 * </p>
 * <ol>
 *     <li><b>意向单创建</b>：{@link ProposalCreatedEvent} 启动 Saga，记忆后续创建投保单所需业务数据；</li>
 *     <li><b>意向单提交</b>：{@link ProposalSubmittedEvent} 触发，自动发 {@link ConvertProposalCommand}
 *         将意向单状态流转为 {@code CONVERTED_TO_APPLICATION}；</li>
 *     <li><b>意向单转换</b>：{@link ProposalConvertedEvent} 触发，用记忆数据构造
 *         {@link ConvertProposalToInsuranceCommand}，创建投保单聚合，启动 {@code IssuanceSaga} 接力；
 *         随后结束本 Saga。</li>
 *     <li><b>意向单作废</b>：{@link ProposalVoidedEvent} 触发时提前结束 Saga（无后续流程）。</li>
 * </ol>
 * <p>
 * 本 Saga 仅负责 Proposal 生命周期内的接力，后续核保/承保/出单由 {@code IssuanceSaga} 事件驱动完成。
 * </p>
 */
@Slf4j
@Saga
public class ProposalIssuanceSaga {

    @Autowired
    private transient CommandGateway commandGateway;

    @Autowired
    private transient PolicyNoGenerator policyNoGenerator;

    /** 保单形态 */
    private PolicyForm policyForm;
    /** 客户ID（投保人），供构造投保单命令的 applicantId */
    private String customerId;
    /** 意向保费（精确保费占位） */
    private BigDecimal intendedPremium;
    /** 保障起期 */
    private java.time.LocalDateTime insurancePeriodStart;
    /** 保障止期 */
    private java.time.LocalDateTime insurancePeriodEnd;
    /** 期望险种编码 */
    private String expectedProductCode;
    /** 险种三级分类（可空） */
    private InsuranceProductType insuranceType;
    /** 租户ID */
    private String tenantId;

    /**
     * 【意向单创建】启动 Saga，记忆后续构造投保单命令所需数据
     */
    @StartSaga
    @SagaEventHandler(associationProperty = "proposalId")
    public void on(ProposalCreatedEvent event) {
        log.info("[ProposalIssuanceSaga] 启动: proposalId={}, tenantId={}",
                event.proposalId(), event.tenantId());
        this.policyForm = event.policyForm();
        this.customerId = event.customerId();
        this.intendedPremium = event.intendedPremium();
        this.insurancePeriodStart = event.insurancePeriodStart();
        this.insurancePeriodEnd = event.insurancePeriodEnd();
        this.expectedProductCode = event.expectedProductCode();
        this.insuranceType = event.insuranceType();
        this.tenantId = event.tenantId();
    }

    /**
     * 【意向单提交】意向单已提交 -> 自动发转换命令，将其状态推进至 CONVERTED_TO_APPLICATION
     */
    @SagaEventHandler(associationProperty = "proposalId")
    public void on(ProposalSubmittedEvent event) {
        log.info("[ProposalIssuanceSaga] 意向单已提交，自动转投保单: proposalId={}", event.proposalId());
        commandGateway.sendAndWait(ConvertProposalCommand.builder()
                .proposalId(event.proposalId())
                .changeReason("意向单提交后自动转投保单")
                .tenantId(event.tenantId())
                .build());
    }

    /**
     * 【意向单转换】Proposal 已 CONVERTED -> 构造投保单命令，创建 Insurance 聚合，结束 Saga
     * <p>
     * InsuranceCreatedEvent 触发 {@code IssuanceSaga} 以 insuranceId 接力完成后续核保/承保/出单流程。
     * </p>
     */
    @EndSaga
    @SagaEventHandler(associationProperty = "proposalId")
    public void on(ProposalConvertedEvent event) {
        String insuranceId = UUID.randomUUID().toString();
        String insuranceNo = policyNoGenerator.generateInsuranceNo();

        // 构造最小参与方清单：仅有投保人 customerId 快照，姓名/证件由后续完善
        InsuredPartyList.HolderInfo holderInfo = new InsuredPartyList.HolderInfo(
                customerId, customerId, null, null, null, null);
        InsuredPartyList minimalPartyList = new InsuredPartyList(
                insuranceId, holderInfo, new ArrayList<>(), new ArrayList<>());

        Money premium = intendedPremium != null ? Money.of(intendedPremium, "CNY") : null;
        List<String> productCodes = expectedProductCode != null
                ? List.of(expectedProductCode) : List.of();

        ConvertProposalToInsuranceCommand command = ConvertProposalToInsuranceCommand.builder()
                .insuranceId(insuranceId)
                .insuranceNo(insuranceNo)
                .proposalId(event.proposalId())
                .policyForm(policyForm)
                .applicantId(customerId)
                .insuredCount(1)
                .exactPremium(premium)
                .insurancePeriodStart(insurancePeriodStart)
                .insurancePeriodEnd(insurancePeriodEnd)
                .productCodes(productCodes)
                .underwritingPriority(0)
                .changeReason("意向单自动转投保单")
                .insuredPartyList(minimalPartyList)
                .insuranceType(insuranceType)
                .tenantId(tenantId)
                .build();

        commandGateway.sendAndWait(command);
        log.info("[ProposalIssuanceSaga] 投保单已创建，后续由 IssuanceSaga 接力: proposalId={}, insuranceId={}",
                event.proposalId(), insuranceId);
    }

    /**
     * 【意向单作废】提前结束 Saga，无后续流程
     */
    @EndSaga
    @SagaEventHandler(associationProperty = "proposalId")
    public void on(ProposalVoidedEvent event) {
        log.warn("[ProposalIssuanceSaga] 意向单已作废，结束 Saga: proposalId={}", event.proposalId());
        SagaLifecycle.end();
    }
}
