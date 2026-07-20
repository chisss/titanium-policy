package com.titanium.policy.query.handler.projection;

import java.time.LocalDateTime;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.common.jpa.BasePersistable;
import com.titanium.policy.common.enums.AnnuityPayoutStatus;
import com.titanium.policy.event.AnnuityBenefitPaidEvent;
import com.titanium.policy.event.AnnuityPayoutStartedEvent;
import com.titanium.policy.query.mapper.PolicyViewMapper;
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
    private final PolicyViewMapper                policyViewMapper;

    /**
     * 投影年金给付期启动事件：建立初始给付计划（已给付 0 期、状态给付中）
     */
    @EventHandler
    @Transactional
    public void on(AnnuityPayoutStartedEvent event) {
        log.info("[年金给付投影] 启动给付期: policyId={}, frequency={}", event.policyId(), event.frequency());
        AnnuityPayoutPlanView view = annuityRepository.findById(event.policyId())
                .orElseGet(AnnuityPayoutPlanView::new);

        // 事件字段 → 读模型的结构映射收敛到 MapStruct（主键取 policyId、频率取 code、金额值对象拆解），消除逐字段 set
        policyViewMapper.applyPayoutStarted(view, event);
        // 初始已给付 0 期 + 给付中状态属创建期语义，由处理器显式赋值，不下沉映射器
        view.setPaidInstallments(0);
        view.setPayoutStatus(AnnuityPayoutStatus.PAYING.getCode());
        stampAuditTime(view);

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

    /**
     * 统一填充读模型审计时间戳：createTime 仅首次创建时写入、updateTime 每次投影刷新。
     * <p>
     * 该逻辑含 {@code now()} 运行时副作用与"仅首次设置"语义，属投影处理器职责，不下沉 MapStruct 映射器。
     * </p>
     *
     * @param view 读模型（继承 {@link BasePersistable}）
     */
    private void stampAuditTime(BasePersistable view) {
        LocalDateTime now = LocalDateTime.now();
        if (view.getCreateTime() == null) {
            view.setCreateTime(now);
        }
        view.setUpdateTime(now);
    }
}
