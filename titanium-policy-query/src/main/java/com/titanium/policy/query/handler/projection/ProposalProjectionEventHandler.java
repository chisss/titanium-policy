package com.titanium.policy.query.handler.projection;

import java.time.LocalDateTime;
import java.util.function.Consumer;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.policy.event.proposal.ProposalConvertedEvent;
import com.titanium.policy.event.proposal.ProposalCreatedEvent;
import com.titanium.policy.event.proposal.ProposalSubmittedEvent;
import com.titanium.policy.event.proposal.ProposalVoidedEvent;
import com.titanium.policy.query.repository.ProposalViewRepository;
import com.titanium.policy.query.view.ProposalView;
import com.titanium.policy.valueobject.proposal.ProposalStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 投保意向单读模型投影事件处理器（CQRS 读侧核心）
 * <p>
 * 订阅意向单域领域事件，将聚合状态变更投影到读模型表 {@code t_proposal_view}，
 * 补齐此前缺失的意向单 CQRS 读模型投影（原 {@code ProposalProjection} 实为直查写模型 JPA），实现真正的读写分离。
 * </p>
 * <p>
 * <b>处理组</b>：复用 {@code policy-query-group}（bootstrap 已配置 TRACKING + DLQ）。
 * <b>幂等性</b>：创建事件用 saveOrUpdate 语义；状态更新事件先查存量再更新，缺失时告警跳过。
 * </p>
 * <p>
 * <b>投影覆盖范围</b>：DRAFT/SUBMITTED/CONVERTED_TO_APPLICATION/VOIDED 全部状态。申请人/标的明细不进领域事件，仅投影 basicInfo 级数据。
 * 注：{@code ProposalConvertedEvent} 当前无命令触发（既有领域缺陷），投影已就绪，待写侧补齐转换命令后自动生效。
 * </p>
 */
@Slf4j
@Component
@ProcessingGroup("policy-query-group")
@RequiredArgsConstructor
public class ProposalProjectionEventHandler {

    private final ProposalViewRepository proposalViewRepository;

    /**
     * 投影意向单创建事件：新建读模型记录，初始状态 DRAFT
     */
    @EventHandler
    @Transactional
    public void on(ProposalCreatedEvent event) {
        log.info("[读模型投影] 意向单创建: proposalId={}, tenantId={}", event.proposalId(), event.tenantId());

        ProposalView view = proposalViewRepository.findByProposalIdAndTenantId(event.proposalId(), event.tenantId())
                .orElseGet(ProposalView::new);

        LocalDateTime now = LocalDateTime.now();
        view.setProposalId(event.proposalId());
        view.setProposalNo(event.proposalNo());
        view.setPolicyForm(event.policyForm());
        view.setChannel(event.channel());
        view.setCustomerId(event.customerId());
        view.setIntendedSumInsured(event.intendedSumInsured());
        view.setIntendedPremium(event.intendedPremium());
        view.setInsurancePeriodStart(event.insurancePeriodStart());
        view.setInsurancePeriodEnd(event.insurancePeriodEnd());
        view.setExpectedProductCode(event.expectedProductCode());
        view.setStatus(ProposalStatus.StatusCode.DRAFT);
        view.setTenantId(event.tenantId());
        if (view.getCreateTime() == null) {
            view.setCreateTime(now);
        }
        view.setUpdateTime(now);

        proposalViewRepository.save(view);
    }

    /**
     * 投影意向单提交事件：状态置为已提交
     */
    @EventHandler
    @Transactional
    public void on(ProposalSubmittedEvent event) {
        applyUpdate(event.proposalId(), event.tenantId(), "意向单提交",
                view -> view.setStatus(ProposalStatus.StatusCode.SUBMITTED));
    }

    /**
     * 投影意向单转投保单事件：状态置为已转投保单
     */
    @EventHandler
    @Transactional
    public void on(ProposalConvertedEvent event) {
        applyUpdate(event.proposalId(), event.tenantId(), "意向单转投保单",
                view -> view.setStatus(ProposalStatus.StatusCode.CONVERTED_TO_APPLICATION));
    }

    /**
     * 投影意向单作废事件：状态置为作废
     */
    @EventHandler
    @Transactional
    public void on(ProposalVoidedEvent event) {
        applyUpdate(event.proposalId(), event.tenantId(), "意向单作废",
                view -> view.setStatus(ProposalStatus.StatusCode.VOIDED));
    }

    /**
     * 通用更新模板：查存量→应用变更→刷新更新时间→保存；缺失时告警跳过保证幂等
     */
    private void applyUpdate(String proposalId, String tenantId, String action, Consumer<ProposalView> mutator) {
        proposalViewRepository.findByProposalIdAndTenantId(proposalId, tenantId).ifPresentOrElse(view -> {
            mutator.accept(view);
            view.setUpdateTime(LocalDateTime.now());
            proposalViewRepository.save(view);
        }, () -> log.warn("[读模型投影] {} 失败：未找到读模型记录 proposalId={}, tenantId={}（可能事件乱序，将由DLQ重试）", action,
                proposalId, tenantId));
    }
}
