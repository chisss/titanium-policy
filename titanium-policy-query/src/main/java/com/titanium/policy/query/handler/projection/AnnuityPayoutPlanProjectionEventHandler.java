package com.titanium.policy.query.handler.projection;

import java.time.LocalDateTime;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.policy.common.enums.AnnuityPayoutStatus;
import com.titanium.policy.event.AnnuityBenefitPaidEvent;
import com.titanium.policy.event.AnnuityPayoutStartedEvent;
import com.titanium.policy.query.repository.AnnuityPayoutPlanViewRepository;
import com.titanium.policy.query.view.AnnuityPayoutPlanView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 年金给付计划读模型投影处理器（CQRS 读侧）
 * <p>
 * 订阅年金给付期启动与逐期给付事件，投影到读模型表 {@code t_annuity_payout_plan}，对外展示年金保单
 * 的给付计划状态、已给付/总期数与下一给付日。年金计划与保单 1:1，故以保单ID作主键幂等 upsert。
 * 纯读模型投影，<b>不发命令、不做跨聚合编排</b>。
 * </p>
 */
@Slf4j
@Component
@ProcessingGroup("policy-query-group")
@RequiredArgsConstructor
public class AnnuityPayoutPlanProjectionEventHandler {

    private final AnnuityPayoutPlanViewRepository annuityRepository;

    /**
     * 投影年金给付期启动事件：建立初始给付计划（已给付 0 期、状态给付中）
     */
    @EventHandler
    @Transactional
    public void on(AnnuityPayoutStartedEvent event) {
        log.info("[年金给付投影] 启动给付期: policyId={}, frequency={}", event.policyId(), event.frequency());
        AnnuityPayoutPlanView view = annuityRepository.findById(event.policyId())
                .orElseGet(AnnuityPayoutPlanView::new);
        view.setId(event.policyId());
        view.setPolicyId(event.policyId());
        view.setStartDate(event.startDate());
        view.setFrequency(event.frequency() != null ? event.frequency().getCode() : null);
        if (event.amountPerInstallment() != null) {
            view.setAmount(event.amountPerInstallment().value());
            view.setCurrency(event.amountPerInstallment().currency());
        }
        view.setTotalInstallments(event.totalInstallments());
        view.setPaidInstallments(0);
        view.setNextPayoutDate(event.nextPayoutDate());
        view.setPayoutStatus(AnnuityPayoutStatus.PAYING.getCode());
        view.setTenantId(event.tenantId());
        LocalDateTime now = LocalDateTime.now();
        if (view.getCreateTime() == null) {
            view.setCreateTime(now);
        }
        view.setUpdateTime(now);
        annuityRepository.save(view);
    }

    /**
     * 投影年金逐期给付事件：推进已给付期数、下一给付日与给付状态
     */
    @EventHandler
    @Transactional
    public void on(AnnuityBenefitPaidEvent event) {
        log.info("[年金给付投影] 给付一期: policyId={}, 第{}期, status={}", event.policyId(), event.installmentNo(),
                event.status());
        AnnuityPayoutPlanView view = annuityRepository.findById(event.policyId()).orElse(null);
        if (view == null) {
            log.warn("[年金给付投影] 未找到给付计划读模型, 跳过给付投影: policyId={}", event.policyId());
            return;
        }
        view.setPaidInstallments(event.paidInstallments());
        view.setNextPayoutDate(event.nextPayoutDate());
        view.setPayoutStatus(event.status() != null ? event.status().getCode() : view.getPayoutStatus());
        if (event.amount() != null) {
            view.setAmount(event.amount().value());
            view.setCurrency(event.amount().currency());
        }
        view.setUpdateTime(LocalDateTime.now());
        annuityRepository.save(view);
    }
}
