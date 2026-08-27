package com.titanium.policy.query.handler.projection;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Consumer;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.policy.common.enums.IssuanceStage;
import com.titanium.policy.event.PolicyActivatedEvent;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.event.PremiumBillingAssociatedEvent;
import com.titanium.policy.event.insurance.InsuranceCreatedEvent;
import com.titanium.policy.event.insurance.InsuranceSubmittedForUnderwritingEvent;
import com.titanium.policy.event.insurance.UnderwritingResultReceivedEvent;
import com.titanium.policy.query.repository.IssuanceProgressViewRepository;
import com.titanium.policy.query.view.IssuanceProgressView;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 出单长流程进度投影。入口服务只建立幂等基线，后续阶段由领域事件单调推进。
 */
@Slf4j
@Component
@ProcessingGroup("policy-query-group")
@RequiredArgsConstructor
public class IssuanceProgressProjectionEventHandler {

    private final IssuanceProgressViewRepository repository;

    @EventHandler
    @Transactional
    public void on(InsuranceCreatedEvent event) {
        if (hasNoIssuanceKey(event.bizNo())) {
            return;
        }
        update(findByBusinessKey(event.bizNo(), event.proposalId(), null, event.tenantId()),
                event.tenantId(), "投保单创建", IssuanceStage.INSURANCE_CREATED, view -> {
                    view.setProposalId(event.proposalId());
                    view.setInsuranceId(event.insuranceId());
                    view.setStandardPremium(event.exactPremium());
                    view.setPayablePremium(event.exactPremium());
                    view.setLineCount(event.insuranceLines() != null ? event.insuranceLines().size() : 0);
                });
    }

    @EventHandler
    @Transactional
    public void on(InsuranceSubmittedForUnderwritingEvent event) {
        if (hasNoIssuanceKey(event.bizNo())) {
            return;
        }
        update(findByBusinessKey(event.bizNo(), null, event.insuranceId(), event.tenantId()),
                event.tenantId(), "提交核保", IssuanceStage.UNDERWRITING, view -> {
                    view.setInsuranceId(event.insuranceId());
                });
    }

    @EventHandler
    @Transactional
    public void on(UnderwritingResultReceivedEvent event) {
        if (hasNoIssuanceKey(event.bizNo())) {
            return;
        }
        IssuanceStage stage = event.resultCode() == ConclusionType.REJECT
                ? IssuanceStage.REJECTED : IssuanceStage.UNDERWRITING;
        update(findByBusinessKey(event.bizNo(), null, event.insuranceId(), event.tenantId()),
                event.tenantId(), "核保结果回写", stage, view -> {
                    view.setInsuranceId(event.insuranceId());
                    view.setUnderwritingId(event.underwritingId());
                    if (event.resultCode() == ConclusionType.REJECT) {
                        view.setRejectCode("UNDERWRITING_REJECTED");
                        view.setRejectReason(event.opinion());
                    }
                });
    }

    @EventHandler
    @Transactional
    public void on(PolicyCreatedEvent event) {
        if (hasNoIssuanceKey(event.bizNo())) {
            return;
        }
        update(findByBusinessKey(event.bizNo(), event.proposalId(), event.insuranceId(), event.tenantId()),
                event.tenantId(), "保单创建", IssuanceStage.POLICY_ISSUED, view -> {
                    view.setProposalId(event.proposalId());
                    view.setInsuranceId(event.insuranceId());
                    view.setPolicyId(event.policyId());
                    view.setUnderwritingId(event.underwritingId());
                    if (event.standardPremium() != null) {
                        view.setStandardPremium(event.standardPremium().value());
                    }
                    view.setPayablePremium(event.premium() != null ? event.premium().value() : null);
                    view.setLineCount(event.policyProducts() != null ? event.policyProducts().size() : 0);
                });
    }

    @EventHandler
    @Transactional
    public void on(PremiumBillingAssociatedEvent event) {
        if (hasNoIssuanceKey(event.bizNo())) {
            return;
        }
        IssuanceStage stage = event.collectionStatus() != null && event.collectionStatus().allowsActivation()
                ? IssuanceStage.POLICY_ISSUED : IssuanceStage.PENDING_COLLECTION;
        update(findByBusinessKey(event.bizNo(), null, null, event.tenantId()), event.tenantId(), "收费单据关联",
                stage, view -> {
                    view.setPolicyId(event.policyId());
                    view.setBillId(event.billId());
                    view.setPaymentOrderId(event.paymentOrderId());
                });
    }

    @EventHandler
    @Transactional
    public void on(PolicyActivatedEvent event) {
        if (hasNoIssuanceKey(event.bizNo())) {
            return;
        }
        update(findByBusinessKey(event.bizNo(), null, event.insuranceId(), event.tenantId()),
                event.tenantId(), "保单生效", IssuanceStage.POLICY_EFFECTIVE,
                view -> view.setPolicyId(event.policyId()));
    }

    private boolean hasNoIssuanceKey(String bizNo) {
        return bizNo == null || bizNo.isBlank();
    }

    private Optional<IssuanceProgressView> findByBusinessKey(String bizNo, String proposalId, String insuranceId,
                                                             String tenantId) {
        if (bizNo != null && !bizNo.isBlank()) {
            return repository.findByBizNoAndTenantId(bizNo, tenantId);
        }
        if (insuranceId != null && !insuranceId.isBlank()) {
            return repository.findByInsuranceIdAndTenantId(insuranceId, tenantId);
        }
        if (proposalId != null && !proposalId.isBlank()) {
            return repository.findByProposalIdAndTenantId(proposalId, tenantId);
        }
        return Optional.empty();
    }

    private void update(Optional<IssuanceProgressView> candidate, String tenantId, String action,
                        IssuanceStage stage, Consumer<IssuanceProgressView> mutator) {
        IssuanceProgressView view = candidate.orElseThrow(() -> new IllegalStateException(
                "出单进度投影缺少基线: action=" + action + ", tenantId=" + tenantId));
        IssuanceStage current = IssuanceStage.fromCode(view.getCurrentStage());
        if ((current == IssuanceStage.POLICY_EFFECTIVE || current == IssuanceStage.REJECTED)
                && current != stage) {
            log.info("[出单进度投影] 忽略终态后的迟到事件: action={}, bizNo={}, currentStage={}, targetStage={}", action,
                    view.getBizNo(), view.getCurrentStage(), stage.getCode());
            return;
        }
        mutator.accept(view);
        advanceStage(view, stage);
        view.setUpdateTime(LocalDateTime.now());
        repository.save(view);
        log.info("[出单进度投影] {}: bizNo={}, stage={}", action, view.getBizNo(), view.getCurrentStage());
    }

    private void advanceStage(IssuanceProgressView view, IssuanceStage target) {
        IssuanceStage current = IssuanceStage.fromCode(view.getCurrentStage());
        // 终态不可被任何迟到事件覆盖；REJECTED 的展示编码虽为 10，但不是比生效更晚的业务阶段。
        if (current == IssuanceStage.POLICY_EFFECTIVE || current == IssuanceStage.REJECTED) {
            return;
        }
        if (current == null || target == IssuanceStage.REJECTED || target.getEnumCode() > current.getEnumCode()) {
            view.setCurrentStage(target.getCode());
        }
    }
}
