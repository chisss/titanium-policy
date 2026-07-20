package com.titanium.policy.query.handler.projection;

import java.time.LocalDateTime;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.common.jpa.BasePersistable;
import com.titanium.policy.event.PolicyEndorsedEvent;
import com.titanium.policy.query.mapper.PolicyViewMapper;
import com.titanium.policy.query.repository.PolicyEndorsementViewRepository;
import com.titanium.policy.query.repository.PolicyViewRepository;
import com.titanium.policy.query.view.PolicyEndorsementView;

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
    private final PolicyViewMapper                policyViewMapper;

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

        // 事件字段 → 批单读模型的结构映射收敛到 MapStruct（类型/大类枚举取 code、版本号与生效日改名），消除逐字段 set
        policyViewMapper.applyEndorsed(view, event);
        // 批改落地时间含 now() 兜底，属处理器职责，不下沉映射器
        view.setEndorsedAt(event.endorsedAt() != null ? event.endorsedAt() : LocalDateTime.now());
        stampAuditTime(view);
        endorsementViewRepository.save(view);

        // 同步保单读模型当前版本号 + 更新时间（增量更新既有 View，非新建型，保留逐字段 set）
        policyViewRepository.findByPolicyIdAndTenantId(event.policyId(), event.tenantId()).ifPresent(policy -> {
            policy.setCurrentVersion(event.versionAfter());
            policy.setUpdateTime(LocalDateTime.now());
            policyViewRepository.save(policy);
        });
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
