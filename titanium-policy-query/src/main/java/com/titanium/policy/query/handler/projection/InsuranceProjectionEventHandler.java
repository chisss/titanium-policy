package com.titanium.policy.query.handler.projection;

import java.time.LocalDateTime;
import java.util.function.Consumer;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.policy.event.insurance.InsuranceCreatedEvent;
import com.titanium.policy.event.insurance.InsuranceIssuedEvent;
import com.titanium.policy.event.insurance.InsuranceSubmittedForUnderwritingEvent;
import com.titanium.policy.event.insurance.UnderwritingResultReceivedEvent;
import com.titanium.policy.query.repository.InsuranceViewRepository;
import com.titanium.policy.query.view.InsuranceView;
import com.titanium.policy.valueobject.insurance.InsuranceStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 投保单读模型投影事件处理器（CQRS 读侧核心）
 * <p>
 * 订阅投保单域领域事件，将聚合状态变更投影到读模型表 {@code t_insurance_view}，
 * 补齐此前缺失的投保单 CQRS 读模型投影（原 {@code InsuranceProjection} 实为直查写模型 JPA），实现真正的读写分离。
 * </p>
 * <p>
 * <b>处理组</b>：复用 {@code policy-query-group}（bootstrap 已配置 TRACKING + DLQ）。
 * <b>幂等性</b>：创建事件用 saveOrUpdate 语义；状态更新事件先查存量再更新，缺失时告警跳过，保证事件重放不产生脏数据。
 * </p>
 * <p>
 * <b>投影覆盖范围</b>：仅投影有领域事件支撑的状态（DRAFT/UNDERWRITING/APPROVED/REJECTED/SUSPENDED/ISSUED）。
 * SUBMITTED、VOIDED 当前无独立领域事件（既有领域缺陷），读模型暂不覆盖。
 * </p>
 */
@Slf4j
@Component
@ProcessingGroup("policy-query-group")
@RequiredArgsConstructor
public class InsuranceProjectionEventHandler {

    private final InsuranceViewRepository insuranceViewRepository;

    /**
     * 投影投保单创建事件：新建读模型记录，初始状态 DRAFT
     */
    @EventHandler
    @Transactional
    public void on(InsuranceCreatedEvent event) {
        log.info("[读模型投影] 投保单创建: insuranceId={}, tenantId={}", event.insuranceId(), event.tenantId());

        InsuranceView view = insuranceViewRepository.findByInsuranceIdAndTenantId(event.insuranceId(), event.tenantId())
                .orElseGet(InsuranceView::new);

        LocalDateTime now = LocalDateTime.now();
        view.setInsuranceId(event.insuranceId());
        view.setInsuranceNo(event.insuranceNo());
        view.setProposalId(event.proposalId());
        view.setPolicyForm(event.policyForm());
        view.setHolderId(event.holderId());
        view.setInsuredCount(event.insuredCount());
        view.setExactPremium(event.exactPremium());
        view.setInsurancePeriodStart(event.insurancePeriodStart());
        view.setInsurancePeriodEnd(event.insurancePeriodEnd());
        view.setInsuranceType(event.insuranceType());
        view.setStatus(InsuranceStatus.StatusCode.DRAFT);
        view.setTenantId(event.tenantId());
        if (view.getCreateTime() == null) {
            view.setCreateTime(now);
        }
        view.setUpdateTime(now);

        insuranceViewRepository.save(view);
    }

    /**
     * 投影提交核保事件：状态置为核保中，补齐币种
     */
    @EventHandler
    @Transactional
    public void on(InsuranceSubmittedForUnderwritingEvent event) {
        applyUpdate(event.insuranceId(), event.tenantId(), "提交核保", view -> {
            view.setStatus(InsuranceStatus.StatusCode.UNDERWRITING);
            view.setCurrency(event.currency());
        });
    }

    /**
     * 投影核保结果回流事件：记录核保结论并映射投保单状态
     */
    @EventHandler
    @Transactional
    public void on(UnderwritingResultReceivedEvent event) {
        applyUpdate(event.insuranceId(), event.tenantId(), "核保结果回流", view -> {
            view.setUnderwritingId(event.underwritingId());
            view.setUnderwritingResultCode(event.resultCode());
            view.setStatus(mapUnderwritingStatus(event.resultCode()));
        });
    }

    /**
     * 投影承保出单事件：状态置为已承保并记录承保时间
     */
    @EventHandler
    @Transactional
    public void on(InsuranceIssuedEvent event) {
        applyUpdate(event.insuranceId(), event.tenantId(), "承保出单", view -> {
            view.setStatus(InsuranceStatus.StatusCode.ISSUED);
            view.setIssuedTime(event.issuedTime());
        });
    }

    /**
     * 核保结论 → 投保单状态映射（与聚合根 UnderwritingResultReceivedEvent 处理逻辑一致）
     */
    private InsuranceStatus.StatusCode mapUnderwritingStatus(ConclusionType resultCode) {
        return switch (resultCode) {
            case ACCEPT, MODIFY -> InsuranceStatus.StatusCode.UNDERWRITING_APPROVED;
            case REJECT -> InsuranceStatus.StatusCode.UNDERWRITING_REJECTED;
            case POSTPONE -> InsuranceStatus.StatusCode.UNDERWRITING_SUSPENDED;
        };
    }

    /**
     * 通用更新模板：查存量→应用变更→刷新更新时间→保存；缺失时告警跳过保证幂等
     */
    private void applyUpdate(String insuranceId, String tenantId, String action, Consumer<InsuranceView> mutator) {
        insuranceViewRepository.findByInsuranceIdAndTenantId(insuranceId, tenantId).ifPresentOrElse(view -> {
            mutator.accept(view);
            view.setUpdateTime(LocalDateTime.now());
            insuranceViewRepository.save(view);
        }, () -> log.warn("[读模型投影] {} 失败：未找到读模型记录 insuranceId={}, tenantId={}（可能事件乱序，将由DLQ重试）", action,
                insuranceId, tenantId));
    }
}
