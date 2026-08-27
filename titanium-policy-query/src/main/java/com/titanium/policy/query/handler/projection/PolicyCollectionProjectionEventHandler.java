package com.titanium.policy.query.handler.projection;

import java.time.LocalDateTime;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.common.jpa.BasePersistable;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.event.PremiumBillingAssociatedEvent;
import com.titanium.policy.event.PremiumCollectedEvent;
import com.titanium.policy.query.repository.PolicyCollectionViewRepository;
import com.titanium.policy.query.repository.PolicyViewRepository;
import com.titanium.policy.query.view.PolicyCollectionView;
import com.titanium.policy.valueobject.policy.CollectionInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单收费信息读模型投影处理器
 * <p>
 * 维护「通过什么方式收了多少钱」这一读侧视图：出单时落地收费方式与应收金额，收费回调时累计
 * 实收并推进收讫状态。同时同步 {@code t_policy_view} 上的收费冗余列，使保单列表页无需 join
 * 即可展示收讫状态。
 * </p>
 * <p>
 * <b>幂等</b>：主键由 policyId 派生（一保单一行），事件重放时 {@code save} 覆盖同一行。
 * </p>
 */
@Slf4j
@Component
@ProcessingGroup("policy-query-group")
@RequiredArgsConstructor
public class PolicyCollectionProjectionEventHandler {

    private final PolicyCollectionViewRepository policyCollectionViewRepository;
    private final PolicyViewRepository           policyViewRepository;

    /**
     * 投影保单创建事件：落地收费方式与应收金额（免支付在出单时即为已收讫状态）。
     */
    @EventHandler
    @Transactional
    public void on(PolicyCreatedEvent event) {
        CollectionInfo collectionInfo = event.collectionInfo();
        if (collectionInfo == null) {
            log.debug("[收费投影] 事件无收费信息，跳过: policyId={}", event.policyId());
            return;
        }
        PolicyCollectionView view = policyCollectionViewRepository.findById(event.policyId())
                .orElseGet(PolicyCollectionView::new);
        view.setId(event.policyId());
        view.setPolicyId(event.policyId());
        view.setCollectionMode(code(collectionInfo));
        view.setBillId(collectionInfo.billId());
        view.setPaymentOrderId(collectionInfo.paymentOrderId());
        view.setPayableAmount(amount(collectionInfo.payableAmount()));
        view.setCollectedAmount(amount(collectionInfo.collectedAmount()));
        view.setCurrency(currency(collectionInfo.payableAmount()));
        view.setCollectionStatus(collectionInfo.collectionStatus() != null
                ? collectionInfo.collectionStatus().getCode()
                : null);
        view.setCollectedTime(collectionInfo.collectedTime());
        view.setTenantId(event.tenantId());
        stampAuditTime(view);
        policyCollectionViewRepository.save(view);
        log.info("[收费投影] 保单创建落地收费信息: policyId={}, 收费方式={}, 应收={}", event.policyId(),
                view.getCollectionMode(), view.getPayableAmount());
    }

    /**
     * 投影收费单据关联事件：回填真实账单ID与支付单ID。
     */
    @EventHandler
    @Transactional
    public void on(PremiumBillingAssociatedEvent event) {
        PolicyCollectionView view = policyCollectionViewRepository.findById(event.policyId())
                .orElseThrow(() -> new IllegalStateException("收费投影缺少保单创建基线: policyId="
                        + event.policyId()));
        view.setBillId(event.billId());
        view.setPaymentOrderId(event.paymentOrderId());
        view.setCollectionStatus(event.collectionStatus() != null ? event.collectionStatus().getCode() : null);
        stampAuditTime(view);
        policyCollectionViewRepository.save(view);
        log.info("[收费投影] 收费单据已关联: policyId={}, billId={}, paymentOrderId={}", event.policyId(),
                event.billId(), event.paymentOrderId());
    }

    /**
     * 投影保费收讫事件：累计实收并推进收讫状态，同步保单主表冗余列。
     */
    @EventHandler
    @Transactional
    public void on(PremiumCollectedEvent event) {
        policyCollectionViewRepository.findById(event.policyId()).ifPresentOrElse(view -> {
            view.setCollectedAmount(amount(event.accumulatedAmount()));
            view.setCollectionStatus(event.collectionStatus() != null ? event.collectionStatus().getCode() : null);
            view.setCollectedTime(event.collectedTime());
            stampAuditTime(view);
            policyCollectionViewRepository.save(view);
            log.info("[收费投影] 保费收讫: policyId={}, 本次实收={}, 累计={}, 状态={}", event.policyId(),
                    event.collectedAmount() != null ? event.collectedAmount().value() : null,
                    view.getCollectedAmount(), view.getCollectionStatus());
        }, () -> log.warn("[收费投影] 收讫事件未找到收费行（投影延迟或出单未落地收费信息）: policyId={}",
                event.policyId()));

        // 同步保单主表冗余列，使列表页无需 join 即可展示收讫状态
        policyViewRepository.findByPolicyIdAndTenantId(event.policyId(), event.tenantId()).ifPresent(policyView -> {
            policyView.setCollectedAmount(amount(event.accumulatedAmount()));
            policyView.setCollectionStatus(event.collectionStatus() != null ? event.collectionStatus().getCode() : null);
            stampAuditTime(policyView);
            policyViewRepository.save(policyView);
        });
    }

    /**
     * 收费方式取 code（空安全）。
     */
    private String code(CollectionInfo collectionInfo) {
        return collectionInfo.collectionMode() != null ? collectionInfo.collectionMode().getCode() : null;
    }

    /**
     * 金额取值（空安全）。
     */
    private java.math.BigDecimal amount(Money money) {
        return money != null ? money.value() : null;
    }

    /**
     * 币种取值（空安全）。
     */
    private String currency(Money money) {
        return money != null ? money.currency() : null;
    }

    /**
     * 盖读模型审计时间戳（投影时间）。create_time 仅首次落地写入，重放/更新只刷新 update_time。
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
