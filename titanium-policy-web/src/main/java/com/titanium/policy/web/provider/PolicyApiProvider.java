package com.titanium.policy.web.provider;

import java.util.List;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.titanium.common.exception.BusinessException;
import com.titanium.metadata.enums.billing.BillingEnum.PaymentMethod;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.response.ApiResponse;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.api.PolicyApi;
import com.titanium.policy.api.request.AccountValueWriteBackRequest;
import com.titanium.policy.api.request.CreatePolicyRequest;
import com.titanium.policy.api.request.RecordPremiumCollectionRequest;
import com.titanium.policy.api.request.maintenance.ApplyPolicyMaintenanceRequest;
import com.titanium.policy.api.request.maintenance.PolicyMaintenanceRetroactiveEvidenceRequest;
import com.titanium.policy.api.response.PolicyEndorsementResponse;
import com.titanium.policy.api.response.PolicyMaintenanceSnapshotResponse;
import com.titanium.policy.api.response.PolicyResponse;
import com.titanium.policy.api.response.PolicyStatusResponse;
import com.titanium.policy.api.response.maintenance.PolicyMaintenanceApplicationResponse;
import com.titanium.policy.api.response.maintenance.PolicyMaintenanceAppliedFieldResponse;
import com.titanium.policy.api.response.maintenance.PolicyMaintenanceAppliedSnapshotResponse;
import com.titanium.policy.api.response.maintenance.PolicyMaintenanceRetroactiveEvidenceResponse;
import com.titanium.policy.application.command.PolicyApplicationService;
import com.titanium.policy.application.query.PolicyAppQueryService;
import com.titanium.policy.command.ApplyPolicyMaintenanceCommand;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.command.CreatePolicyDirectlyCommand;
import com.titanium.policy.command.RecordPremiumCollectionCommand;
import com.titanium.policy.query.query.FindPolicyByIdQuery;
import com.titanium.policy.query.result.PolicyEndorsementQueryResult;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceApplicationReceipt;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceFieldChange;
import com.titanium.policy.valueobject.maintenance.PolicyMaintenanceRetroactiveEvidence;
import com.titanium.policy.web.mapper.PolicyWebMapper;

import lombok.RequiredArgsConstructor;

/**
 * 保单契约实现（Provider）
 * <p>
 * 承接 {@link PolicyApi} Feign 契约，面向其它微服务的远程调用。路径由 {@link PolicyApi} 的
 * {@code @RequestMapping("/api/v1/policies")} 唯一定义，本类通过 {@code implements} 继承，
 * <b>不重复标注、不篡改</b>。职责仅为协议转换（DTO ⇄ 用例 Input）+ 调用应用层门面，零业务逻辑。
 * 与面向后台/端上的 {@code PolicyController} 平行收敛到同一 {@link PolicyApplicationService}。
 * </p>
 */
@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
public class PolicyApiProvider implements PolicyApi {

    private final PolicyApplicationService policyApplicationService;

    private final PolicyAppQueryService    policyAppQueryService;

    private final PolicyWebMapper          policyWebMapper;

    @Override
    public ApiResponse<PolicyMaintenanceApplicationResponse> applyMaintenance(
            String policyId,
            ApplyPolicyMaintenanceRequest request,
            String operatorId,
            String tenantId) {
        ApplyPolicyMaintenanceCommand command = new ApplyPolicyMaintenanceCommand(
                policyId, request.requestId(), request.maintenanceCaseId(), request.expectedPolicyVersion(),
                request.requestPayloadHash(), request.proposedSnapshotHash(), request.effectiveTimeType(),
                request.effectiveAt(), request.changeSummary(), request.changes().stream()
                        .map(change -> new PolicyMaintenanceFieldChange(
                                change.itemCode(), change.objectId(), change.fieldCode(),
                                change.dataType(), change.canonicalValue()))
                        .toList(),
                request.stateAction(), request.stateReason(), request.terminationReason(),
                toRetroactiveEvidence(request.retroactiveEvidence()),
                operatorId, tenantId);
        return ApiResponse.success(toResponse(policyApplicationService.applyMaintenance(command)));
    }

    @Override
    public ApiResponse<String> createPolicy(CreatePolicyRequest dto, String tenantId) {
        // 协议转换：远程 DTO → 领域命令，收敛到同一应用层门面
        CreatePolicyCommand command = policyWebMapper.toCommand(dto, tenantId);
        String policyId = policyApplicationService.createPolicy(command);
        return ApiResponse.success(policyId);
    }

    @Override
    public ApiResponse<String> createPolicyDirectly(CreatePolicyRequest dto, String tenantId) {
        // 一步出单：DTO → 一步出单命令，收敛到 application 门面
        CreatePolicyDirectlyCommand command = policyWebMapper.toDirectCommand(dto, tenantId);
        String policyId = policyApplicationService.createPolicyDirectly(command);
        return ApiResponse.success(policyId);
    }

    @Override
    public ApiResponse<PolicyResponse> getPolicy(String policyId, String tenantId) {
        // 读：构造 FindPolicyByIdQuery 交读门面派发（QueryGateway → PolicyView），未命中返回 404 码
        return policyAppQueryService.findById(new FindPolicyByIdQuery(policyId, tenantId))
                .map(policyWebMapper::toResponse)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(PolicyErrorCode.POLICY_NOT_EXIST, "保单不存在: " + policyId));
    }

    @Override
    public ApiResponse<PolicyStatusResponse> getPolicyStatus(String policyId, String tenantId) {
        return policyAppQueryService.findById(new FindPolicyByIdQuery(policyId, tenantId))
                .map(policyWebMapper::toStatusResponse)
                .map(ApiResponse::success)
                .orElseGet(() -> ApiResponse.error(PolicyErrorCode.POLICY_NOT_EXIST, "保单不存在: " + policyId));
    }

    @Override
    public ApiResponse<PolicyMaintenanceSnapshotResponse> getMaintenanceSnapshot(
            String policyId,
            String tenantId) {
        try {
            return policyAppQueryService.findMaintenanceSnapshot(policyId, tenantId)
                    .map(policyWebMapper::toMaintenanceSnapshotResponse)
                    .map(ApiResponse::success)
                    .orElseGet(() -> ApiResponse.error(
                            PolicyErrorCode.POLICY_NOT_EXIST, "保单不存在: " + policyId));
        } catch (BusinessException exception) {
            return new ApiResponse<>(exception.getErrorCode(), exception.getMessage(), null);
        }
    }

    @Override
    public ApiResponse<List<PolicyEndorsementResponse>> getEndorsements(String policyId, String tenantId) {
        return ApiResponse.success(policyAppQueryService.findEndorsements(policyId, tenantId).stream()
                .map(this::toEndorsementResponse)
                .toList());
    }

    @Override
    public ApiResponse<Void> issuePolicy(String policyId, String operatorId, String tenantId) {
        policyApplicationService.issuePolicy(policyId, operatorId, tenantId);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> activatePolicy(String policyId, String tenantId) {
        policyApplicationService.activatePolicy(policyId, tenantId);
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> recordPremiumCollection(
            String policyId,
            RecordPremiumCollectionRequest request,
            String operatorId,
            String tenantId) {
        policyApplicationService.recordPremiumCollection(new RecordPremiumCollectionCommand(
                policyId, request.paymentId(), request.paymentNo(),
                Money.of(request.collectedAmount(), request.currency()),
                PaymentMethod.fromCode(request.paymentMethod()), request.collectedTime(),
                operatorId, tenantId));
        return ApiResponse.success();
    }

    @Override
    public ApiResponse<Void> writeBackAccountValue(String policyId, AccountValueWriteBackRequest dto, String tenantId) {
        // 协议转换：投资域回写请求 → 更新账户价值命令，收敛到 application 门面
        policyApplicationService.updateAccountValue(policyId, dto.getAccountId(), dto.getAccountValue(),
                dto.getCurrency(), tenantId);
        return ApiResponse.success();
    }

    private PolicyMaintenanceApplicationResponse toResponse(PolicyMaintenanceApplicationReceipt receipt) {
        PolicyMaintenanceAppliedSnapshotResponse snapshot = new PolicyMaintenanceAppliedSnapshotResponse(
                receipt.appliedSnapshot().storageKey(), receipt.appliedSnapshot().contentHash(),
                receipt.appliedSnapshot().policyVersion(), receipt.appliedSnapshot().capturedAt());
        return new PolicyMaintenanceApplicationResponse(
                receipt.requestId(), receipt.endorsementNo(), receipt.expectedPolicyVersion(),
                receipt.actualPolicyVersion(), receipt.applicationHash(), snapshot,
                receipt.appliedFields().stream()
                        .map(field -> new PolicyMaintenanceAppliedFieldResponse(
                                field.itemCode(), field.objectId(), field.fieldCode(),
                                field.dataType(), field.canonicalValue()))
                        .toList(),
                receipt.appliedAt(), receipt.stateAction(),
                receipt.statusBefore() == null ? null : receipt.statusBefore().name(),
                receipt.statusAfter() == null ? null : receipt.statusAfter().name(),
                toRetroactiveEvidenceResponse(receipt.retroactiveEvidence()));
    }

    private PolicyMaintenanceRetroactiveEvidence toRetroactiveEvidence(
            PolicyMaintenanceRetroactiveEvidenceRequest evidence) {
        if (evidence == null) {
            return null;
        }
        return new PolicyMaintenanceRetroactiveEvidence(
                evidence.analysisId(), evidence.analysisVersion(), evidence.analysisResultHash(),
                evidence.periodRecalculationId(), evidence.periodRecalculationVersion(),
                evidence.productRecalculationId(), evidence.productRecalculationVersion(),
                evidence.productInputHash(), evidence.productResultHash(), evidence.billingBatchId(),
                evidence.billingBatchResultHash(), evidence.billingStatus(), evidence.billingResolutionId(),
                evidence.billingResolutionResultHash(), evidence.targetAccountingPeriod(),
                evidence.resolvedLineCount());
    }

    private PolicyMaintenanceRetroactiveEvidenceResponse toRetroactiveEvidenceResponse(
            PolicyMaintenanceRetroactiveEvidence evidence) {
        if (evidence == null) {
            return null;
        }
        return new PolicyMaintenanceRetroactiveEvidenceResponse(
                evidence.analysisId(), evidence.analysisVersion(), evidence.analysisResultHash(),
                evidence.periodRecalculationId(), evidence.periodRecalculationVersion(),
                evidence.productRecalculationId(), evidence.productRecalculationVersion(),
                evidence.productInputHash(), evidence.productResultHash(), evidence.billingBatchId(),
                evidence.billingBatchResultHash(), evidence.billingStatus(), evidence.billingResolutionId(),
                evidence.billingResolutionResultHash(), evidence.targetAccountingPeriod(),
                evidence.resolvedLineCount());
    }

    private PolicyEndorsementResponse toEndorsementResponse(PolicyEndorsementQueryResult result) {
        return new PolicyEndorsementResponse(
                result.getEndorsementNo(), result.getPolicyId(), result.getUpdateType(), result.getCategory(),
                result.getPolicyVersion(), result.getEffectiveDate(), result.getChangeSummary(),
                result.isRequiresPremiumRecalc(), result.getSourceMaintenanceId(), result.getOperatorId(),
                result.getEndorsedAt(), result.getTenantId());
    }
}
