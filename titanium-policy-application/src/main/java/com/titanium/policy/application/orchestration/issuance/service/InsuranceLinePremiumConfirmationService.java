package com.titanium.policy.application.orchestration.issuance.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.titanium.common.exception.BusinessException;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.application.orchestration.issuance.assembler.ConfirmedPremiumRequestAssembler;
import com.titanium.policy.application.orchestration.issuance.validator.ConfirmedPremiumRequestValidator;
import com.titanium.policy.entity.insurance.InsuranceLine;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.port.ConfirmedPremiumPricingPort;
import com.titanium.policy.valueobject.pricing.ConfirmedPremiumRequest;
import com.titanium.policy.valueobject.pricing.ConfirmedPremiumResult;
import com.titanium.policy.valueobject.pricing.PremiumCalculationReference;

import lombok.RequiredArgsConstructor;

/**
 * 出单险种段确认保费编排。
 * <p>
 * 每个有效险种段独立调用 Product CONFIRM，Policy 只冻结和汇总返回结果，不再解释费率、舍入或
 * 核保调整规则。任一段失败即阻断出单，禁止回退请求中的报价或旧 {@code exactPremium}。
 * </p>
 * <p>
 * 本次整改：4 个魔法字符串常量（错误码/状态/用途/调整类型）收敛为 {@link PolicyErrorCode} 与
 * common/enums 业务枚举；请求装配下沉 {@link ConfirmedPremiumRequestAssembler}；校验链收敛
 * {@link ConfirmedPremiumRequestValidator}（红线 16/17/18/19）。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class InsuranceLinePremiumConfirmationService {

    private final ConfirmedPremiumPricingPort    confirmedPremiumPricingPort;
    private final ConfirmedPremiumRequestValidator validator;
    private final ConfirmedPremiumRequestAssembler requestAssembler;

    /**
     * 确认全部有效险种段保费。
     */
    public ConfirmationSummary confirm(List<InsuranceLine> lines, InsuredPartyList insuredPartyList,
                                       String issuanceReference, String bizNo, LocalDateTime businessTime,
                                       String tenantId, boolean includeUnderwritingAdjustments) {
        return confirm(lines, insuredPartyList, issuanceReference, bizNo, businessTime, tenantId, null, 1,
                includeUnderwritingAdjustments);
    }

    /**
     * 确认全部有效险种段保费，并携带渠道合同选择所需的业务上下文。
     */
    public ConfirmationSummary confirm(List<InsuranceLine> lines, InsuredPartyList insuredPartyList,
                                       String issuanceReference, String bizNo, LocalDateTime businessTime,
                                       String tenantId, String channelId, int policyYear,
                                       boolean includeUnderwritingAdjustments) {
        validator.validateLines(lines);
        List<InsuranceLine> confirmedLines = new ArrayList<>();
        List<PremiumCalculationReference> references = new ArrayList<>();
        Money standardTotal = null;
        Money total = null;
        for (InsuranceLine line : lines) {
            if (!line.countsTowardTotalPremium()) {
                confirmedLines.add(line);
                continue;
            }
            ConfirmedPremiumResult result = confirmLine(line, insuredPartyList, issuanceReference, bizNo, businessTime,
                    tenantId, channelId, policyYear, includeUnderwritingAdjustments);
            Money standardPremium = Money.of(result.standardPremium(), result.currency());
            Money confirmedPremium = Money.of(result.totalPremium(), result.currency());
            standardTotal = standardTotal == null ? standardPremium : standardTotal.add(standardPremium);
            total = total == null ? confirmedPremium : total.add(confirmedPremium);
            confirmedLines.add(line.withConfirmedPremium(confirmedPremium));
            references.add(new PremiumCalculationReference(result.calculationId(), result.resultHash(),
                    result.productId(), result.productVersion(), result.pricingPlanVersion(), result.totalPremium(),
                    result.currency(), line.lineId()));
        }
        validator.validateBillableExists(total, references);
        return new ConfirmationSummary(List.copyOf(confirmedLines), standardTotal, total, List.copyOf(references));
    }

    private ConfirmedPremiumResult confirmLine(InsuranceLine line, InsuredPartyList insuredPartyList,
                                               String issuanceReference, String bizNo, LocalDateTime businessTime,
                                               String tenantId, String channelId, int policyYear,
                                               boolean includeUnderwritingAdjustments) {
        try {
            ConfirmedPremiumRequest request = requestAssembler.assemble(line, insuredPartyList, issuanceReference,
                    bizNo, businessTime, tenantId, channelId, policyYear, includeUnderwritingAdjustments);
            validator.validate(request);
            ConfirmedPremiumResult result = confirmedPremiumPricingPort.confirm(request);
            validator.validateResult(line, result);
            return result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException("险种段确认保费失败: lineNo=" + line.lineNo() + ", productId=" + line.productId(),
                    PolicyErrorCode.ISSUANCE_PREMIUM_CONFIRMATION_FAILED, exception);
        }
    }

    /**
     * 一次出单的逐段确认结果。
     */
    public record ConfirmationSummary(List<InsuranceLine> lines, Money standardPremium, Money totalPremium,
                                      List<PremiumCalculationReference> calculationReferences) {
    }
}
