package com.titanium.policy.query.handler;

import java.time.LocalDateTime;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.policy.event.PolicyEndorsedEvent;
import com.titanium.policy.query.entity.PolicyEndorsementView;
import com.titanium.policy.query.repository.PolicyEndorsementViewRepository;
import com.titanium.policy.query.repository.PolicyViewRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 批单读模型投影
 * <p>
 * 订阅 PolicyEndorsedEvent，写入批单流水读模型 t_policy_endorsement_view，并同步刷新
 * 保单读模型 t_policy_view 的当前版本号与更新时间。与现有 PolicyProjectionEventHandler
 * 共用 policy-query-group 处理组（复用 DLQ/tracking 配置）。
 * </p>
 */
@Slf4j
@Component
@ProcessingGroup("policy-query-group")
@RequiredArgsConstructor
public class EndorsementProjectionEventHandler {

    private final PolicyEndorsementViewRepository endorsementViewRepository;
    private final PolicyViewRepository            policyViewRepository;

    /**
     * 投影批改事件：写批单流水 + 刷新保单读模型版本
     */
    @EventHandler
    @Transactional
    public void on(PolicyEndorsedEvent event) {
        log.info("[读模型投影] 保单批改: policyId={}, endorsementNo={}, type={}", event.policyId(),
                event.endorsementNo(), event.updateType().getCode());

        PolicyEndorsementView view = endorsementViewRepository.findById(event.endorsementNo())
                .orElseGet(PolicyEndorsementView::new);
        view.setEndorsementNo(event.endorsementNo());
        view.setPolicyId(event.policyId());
        view.setUpdateType(event.updateType().getCode());
        view.setCategory(event.category().getCode());
        view.setPolicyVersion(event.versionAfter());
        view.setEffectiveDate(event.endorsementEffectiveDate());
        view.setChangeSummary(event.changeSummary());
        view.setRequiresPremiumRecalc(event.requiresPremiumRecalc());
        view.setSourceMaintenanceId(event.sourceMaintenanceId());
        view.setOperatorId(event.operatorId());
        view.setTenantId(event.tenantId());
        view.setEndorsedAt(event.endorsedAt() != null ? event.endorsedAt() : LocalDateTime.now());
        endorsementViewRepository.save(view);

        // 同步保单读模型当前版本号 + 更新时间
        policyViewRepository.findByPolicyIdAndTenantId(event.policyId(), event.tenantId()).ifPresent(policy -> {
            policy.setCurrentVersion(event.versionAfter());
            policy.setUpdateTime(LocalDateTime.now());
            policyViewRepository.save(policy);
        });
    }
}
