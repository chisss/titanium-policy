package com.titanium.policy.application.command;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.metadata.enums.BaseEnum;
import com.titanium.policy.common.enums.IssuanceStage;
import com.titanium.policy.query.repository.IssuanceProgressViewRepository;
import com.titanium.policy.query.view.IssuanceProgressView;
import com.titanium.policy.valueobject.IssuanceRequest;
import com.titanium.policy.valueobject.IssuanceResult;

import lombok.RequiredArgsConstructor;

/**
 * 在独立事务中建立出单进度基线，确保异步事件投影始终能查询到关联行。
 */
@Service
@RequiredArgsConstructor
class IssuanceProgressBaselineWriter {

    private final IssuanceProgressViewRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(IssuanceRequest request, IssuanceResult result) {
        IssuanceProgressView view = new IssuanceProgressView();
        LocalDateTime now = LocalDateTime.now();
        view.setId(request.tenantId() + "_" + request.bizNo());
        view.setBizNo(request.bizNo());
        view.setTenantId(request.tenantId());
        view.setCreateTime(now);
        view.setUpdateTime(now);
        view.setMarketPackageId(request.marketPackageId());
        view.setIssuanceStrategy(code(result.issuanceStrategy()));
        view.setIssuanceMode(code(result.issuanceMode()));
        view.setCurrentStage(code(result.currentStage()));
        view.setProductId(request.mainProductId());
        view.setHolderCustomerId(request.holderCustomerId());
        view.setProposalId(result.proposalId());
        view.setInsuranceId(result.insuranceId());
        view.setPolicyId(result.firstPolicyId());
        view.setUnderwritingId(result.underwritingId());
        view.setBillId(result.billId());
        view.setPaymentOrderId(result.paymentOrderId());
        view.setStandardPremium(result.standardPremium() != null ? result.standardPremium().value() : null);
        view.setPayablePremium(result.payablePremium() != null ? result.payablePremium().value() : null);
        view.setLineCount(request.planLines() != null ? request.planLines().size() : 0);
        view.setRejectCode(result.rejectCode());
        view.setRejectReason(result.rejectReason());
        // 强制在独立事务返回前触发唯一约束校验，调用方才能把并发重复提交恢复为首次结果。
        repository.saveAndFlush(view);
    }

    /**
     * 仅当基线仍未关联任何业务单据时，将其更新为业务拒绝终态。
     *
     * @return 是否成功更新纯基线
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markRejectedIfUntouched(IssuanceRequest request, IssuanceResult rejected) {
        int updated = repository.markUntouchedBaselineRejected(request.bizNo(), request.tenantId(),
                IssuanceStage.ACCEPTED.getCode(), IssuanceStage.REJECTED.getCode(), rejected.rejectCode(),
                rejected.rejectReason(), LocalDateTime.now());
        return updated == 1;
    }

    /**
     * 仅删除没有关联任何业务单据的纯受理基线。
     *
     * @return 是否释放纯基线
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean releaseIfUntouched(IssuanceRequest request) {
        return repository.deleteUntouchedAcceptedBaseline(request.bizNo(), request.tenantId(),
                IssuanceStage.ACCEPTED.getCode()) == 1;
    }

    private String code(BaseEnum value) {
        return value != null ? value.getCode() : null;
    }
}
