package com.titanium.policy.command;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.entity.insurance.InsuredPartyList;

import lombok.Builder;

/**
 * 将投保意向单转为投保单命令
 * <p>
 * 用于将投保意向单转换为投保单聚合根
 * </p>
 *
 * @param insuranceId 投保单聚合根唯一标识
 * @param insuranceNo 投保单编号
 * @param proposalId 投保意向单ID
 * @param policyForm 保单形态
 * @param applicantId 投保人ID
 * @param insuredCount 被保险人数量
 * @param exactPremium 精确保费
 * @param insurancePeriodStart 保障期限起期
 * @param insurancePeriodEnd 保障期限止期
 * @param productCodes 投保险种编码列表
 * @param underwritingPriority 核保优先级
 * @param changeReason 变更原因
 * @param insuredPartyList 投保参与方清单（含投保人/被保险人/受益人快照，可空）
 * @param insuranceType 险种三级分类（可空，向后兼容存量事件）
 * @param tenantId 租户ID
 * @param sumInsured 基本保额，供 billing 计算真实保费（null 时回退 exactPremium）
 * @param paymentMode 缴费模式 code（LUMP_SUM/ANNUAL/MONTHLY），null 时由 billing 产品配置决定
 * @param premiumPaymentYears 缴费年数（0 表示未知）
 */
@Builder
public record ConvertProposalToInsuranceCommand(@TargetAggregateIdentifier String insuranceId, String insuranceNo,
                                                String proposalId, PolicyForm policyForm, String applicantId,
                                                int insuredCount, Money exactPremium,
                                                LocalDateTime insurancePeriodStart, LocalDateTime insurancePeriodEnd,
                                                List<String> productCodes, int underwritingPriority,
                                                String changeReason, InsuredPartyList insuredPartyList,
                                                InsuranceProductType insuranceType,
                                                String tenantId,
                                                BigDecimal sumInsured,
                                                String paymentMode,
                                                int premiumPaymentYears) {
}
