package com.titanium.policy.command;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.metadata.enums.billing.PremiumCollectionMode;
import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.entity.insurance.InsuranceLine;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.valueobject.policy.ChannelInfo;

import lombok.Builder;

/**
 * 将投保意向单转为投保单命令（三步出单第二步）
 * <p>
 * 意向段 {@code ProposalLine}（意向保额/保费）在此精化为投保段 {@code InsuranceLine}
 * （试算保费 + 完整标的），以结构化段列表取代原 {@code productCodes} 裸编码。
 * </p>
 *
 * @param insuranceId          投保单聚合根唯一标识
 * @param insuranceNo          投保单编号
 * @param proposalId           投保意向单ID
 * @param policyForm           保单形态
 * @param applicantId          投保人客户ID
 * @param insuredCount         被保险人数量
 * @param exactPremium         投保单总保费（Σ 段保费）
 * @param insurancePeriodStart 保障期限起期
 * @param insurancePeriodEnd   保障期限止期
 * @param insuranceLines       险种段列表（L2，自意向段精化而来）
 * @param underwritingPriority 核保优先级
 * @param changeReason         变更原因
 * @param insuredPartyList     参与方清单（投保人/被保险人/受益人）
 * @param insuranceType        主险险种三级分类
 * @param collectionMode       收费方式
 * @param channelInfo          渠道信息
 * @param bizNo                出单业务流水号
 * @param marketPackageId      营销包ID（弱引用，可空）
 * @param tenantId             租户ID
 * @param sumInsured           主险基本保额（billing 保费计算入参）
 * @param paymentMode          主险缴费模式 code
 * @param premiumPaymentYears  主险缴费年数（0 表示未知）
 */
@Builder
public record ConvertProposalToInsuranceCommand(@TargetAggregateIdentifier String insuranceId, String insuranceNo,
                                                String proposalId, PolicyForm policyForm, String applicantId,
                                                int insuredCount, Money exactPremium,
                                                LocalDateTime insurancePeriodStart, LocalDateTime insurancePeriodEnd,
                                                List<InsuranceLine> insuranceLines, int underwritingPriority,
                                                String changeReason, InsuredPartyList insuredPartyList,
                                                InsuranceProductType insuranceType,
                                                PremiumCollectionMode collectionMode, ChannelInfo channelInfo,
                                                String bizNo, String marketPackageId, String tenantId,
                                                BigDecimal sumInsured, String paymentMode, int premiumPaymentYears) {
}
