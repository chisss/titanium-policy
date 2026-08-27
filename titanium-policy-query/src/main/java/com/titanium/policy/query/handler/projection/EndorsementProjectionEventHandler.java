package com.titanium.policy.query.handler.projection;

import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.common.jpa.BasePersistable;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.event.PolicyEndorsedEvent;
import com.titanium.policy.event.PolicyMaintenanceAppliedEvent;
import com.titanium.policy.event.PolicyMaintenanceStateAppliedEvent;
import com.titanium.policy.query.mapper.PolicyViewMapper;
import com.titanium.policy.query.repository.PolicyEndorsementViewRepository;
import com.titanium.policy.query.repository.PolicyProductViewRepository;
import com.titanium.policy.query.repository.PolicyViewRepository;
import com.titanium.policy.query.view.PolicyEndorsementView;
import com.titanium.policy.query.view.PolicyProductView;
import com.titanium.policy.query.view.PolicyView;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceExecutionState;

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
    private final PolicyProductViewRepository     policyProductViewRepository;
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

    /** 投影正式保全应用事件：批单、业务版本和实际字段在同一事务刷新。 */
    @EventHandler
    @Transactional
    public void on(PolicyMaintenanceAppliedEvent event) {
        log.info("[读模型投影] Policy 保全应用: policyId={}, endorsementNo={}, requestId={}",
                event.policyId(), event.endorsementNo(), event.requestId());
        PolicyEndorsementView view = endorsementViewRepository.findById(event.endorsementNo())
                .orElseGet(PolicyEndorsementView::new);
        view.setEndorsementNo(event.endorsementNo());
        view.setPolicyId(event.policyId());
        view.setUpdateType(event.updateType().getCode());
        view.setCategory(event.category().getCode());
        view.setPolicyVersion(Math.toIntExact(event.actualPolicyVersion()));
        view.setEffectiveDate(event.effectiveAt());
        view.setChangeSummary(event.changeSummary());
        view.setRequiresPremiumRecalc(event.updateType().needsPremiumRecalc());
        view.setSourceMaintenanceId(event.sourceMaintenanceId());
        view.setOperatorId(event.operatorId());
        view.setEndorsedAt(event.appliedAt());
        view.setTenantId(event.tenantId());
        stampAuditTime(view);
        endorsementViewRepository.save(view);

        policyViewRepository.findByPolicyIdAndTenantId(event.policyId(), event.tenantId()).ifPresent(policy -> {
            policy.setCurrentVersion(Math.toIntExact(event.actualPolicyVersion()));
            applyExecutionState(event.policyId(), event.tenantId(), policy, event.executionStateAfter());
            policy.setUpdateTime(LocalDateTime.now());
            policyViewRepository.save(policy);
        });
    }

    /** 投影状态类保全的统一批单、版本和字段实际值。 */
    @EventHandler
    @Transactional
    public void on(PolicyMaintenanceStateAppliedEvent event) {
        log.info("[读模型投影] Policy 状态保全应用: policyId={}, endorsementNo={}, action={}",
                event.policyId(), event.endorsementNo(), event.stateAction());
        PolicyEndorsementView view = endorsementViewRepository.findById(event.endorsementNo())
                .orElseGet(PolicyEndorsementView::new);
        view.setEndorsementNo(event.endorsementNo());
        view.setPolicyId(event.policyId());
        view.setUpdateType(event.applicationType());
        view.setCategory(event.category().getCode());
        view.setPolicyVersion(Math.toIntExact(event.actualPolicyVersion()));
        view.setEffectiveDate(event.effectiveAt());
        view.setChangeSummary(event.changeSummary());
        view.setRequiresPremiumRecalc(false);
        view.setSourceMaintenanceId(event.sourceMaintenanceId());
        view.setOperatorId(event.operatorId());
        view.setEndorsedAt(event.appliedAt());
        view.setTenantId(event.tenantId());
        stampAuditTime(view);
        endorsementViewRepository.save(view);

        policyViewRepository.findByPolicyIdAndTenantId(event.policyId(), event.tenantId()).ifPresent(policy -> {
            policy.setCurrentVersion(Math.toIntExact(event.actualPolicyVersion()));
            applyExecutionState(event.policyId(), event.tenantId(), policy, event.executionStateAfter());
            policy.setUpdateTime(LocalDateTime.now());
            policyViewRepository.save(policy);
        });
    }

    private void applyExecutionState(
            String policyId,
            String tenantId,
            PolicyView policy,
            PolicyMaintenanceExecutionState executionState) {
        if (executionState == null) {
            return;
        }
        if (executionState.insuredPartyList() != null
                && executionState.insuredPartyList().holderInfo() != null) {
            policy.setPolicyHolderPhone(executionState.insuredPartyList().holderInfo().phone());
        }
        if (executionState.policyProducts() == null) {
            return;
        }
        List<PolicyProductView> productViews = policyProductViewRepository
                .findByPolicyIdAndTenantIdOrderByLineNoAsc(policyId, tenantId);
        for (PolicyProduct product : executionState.policyProducts()) {
            productViews.stream()
                    .filter(view -> product.policyProductId().equals(view.getPolicyProductId()))
                    .findFirst()
                    .ifPresent(view -> {
                        view.setSumInsured(product.sumInsured() == null ? null : product.sumInsured().value());
                        view.setUpdateTime(LocalDateTime.now());
                        policyProductViewRepository.save(view);
                    });
            if (product.isMain()) {
                policy.setSumInsured(product.sumInsured() == null ? null : product.sumInsured().value());
            }
        }
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
