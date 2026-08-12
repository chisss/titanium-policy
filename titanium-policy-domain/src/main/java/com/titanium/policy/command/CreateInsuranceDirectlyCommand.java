package com.titanium.policy.command;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

import com.titanium.metadata.enums.billing.PremiumCollectionMode;
import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.policy.entity.insurance.InsuranceLine;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.valueobject.policy.ChannelInfo;

/**
 * 直接创建投保单命令（两步出单，跳过意向单）
 * <p>
 * 以结构化险种段 {@code insuranceLines} 取代原 {@code productCodes}（{@code List<String>} 裸编码），
 * 使每段的保额/保费/期间/缴费/标的可独立表达，核保得以按段出结论。
 * </p>
 *
 * @param insuranceId          投保单ID
 * @param insuranceNo          投保单编号
 * @param policyForm           保单形态
 * @param holderId             投保人客户ID
 * @param insuredCount         被保险人数
 * @param exactPremium         投保单总保费（Σ 段保费）
 * @param insurancePeriodStart 保障起期
 * @param insurancePeriodEnd   保障止期
 * @param insuranceLines       险种段列表（L2，1..N）
 * @param underwritingPriority 核保优先级
 * @param insuredPartyList     参与方清单
 * @param insuranceType        主险险种三级分类
 * @param collectionMode       收费方式
 * @param channelInfo          渠道信息
 * @param bizNo                出单业务流水号
 * @param marketPackageId      营销包ID（弱引用，可空）
 * @param tenantId             租户ID
 * @param sumInsured           主险基本保额（billing 保费计算入参）
 * @param paymentMode          主险缴费模式 code
 * @param premiumPaymentYears  主险缴费年数
 */
public record CreateInsuranceDirectlyCommand(@TargetAggregateIdentifier String insuranceId, String insuranceNo,
                                             PolicyForm policyForm, String holderId, int insuredCount,
                                             BigDecimal exactPremium, LocalDateTime insurancePeriodStart,
                                             LocalDateTime insurancePeriodEnd, List<InsuranceLine> insuranceLines,
                                             int underwritingPriority, InsuredPartyList insuredPartyList,
                                             InsuranceProductType insuranceType, PremiumCollectionMode collectionMode,
                                             ChannelInfo channelInfo, String bizNo, String marketPackageId,
                                             String tenantId, BigDecimal sumInsured, String paymentMode,
                                             int premiumPaymentYears) {
}
