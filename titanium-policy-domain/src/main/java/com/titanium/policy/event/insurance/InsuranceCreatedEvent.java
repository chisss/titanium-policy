package com.titanium.policy.event.insurance;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.titanium.metadata.enums.billing.PremiumCollectionMode;
import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.policy.entity.insurance.InsuranceLine;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.valueobject.policy.ChannelInfo;

/**
 * 投保单创建事件
 * <p>
 * 🔴 <b>核心改动：以结构化险种段 {@code insuranceLines} 取代 {@code productCodes}</b>
 * （{@code List<String>} 裸编码列表）。裸编码只能表达「投保了哪几个产品」，无法承载每段的
 * 保额、保费、保障期间、缴费条件与标的——而这些在一单多险场景下按段各不相同，
 * 且核保需按段出结论。
 * </p>
 * <p>
 * 保留 {@code exactPremium}/{@code sumInsured}/{@code paymentMode}/{@code premiumPaymentYears}
 * 作为主险的便捷冗余（出单 Saga 与 billing 保费计算的既有消费点依赖它们），真相在段上。
 * </p>
 *
 * @param insuranceId          投保单ID
 * @param insuranceNo          投保单编号
 * @param proposalId           关联意向单ID（两步出单为 null）
 * @param policyForm           保单形态
 * @param holderId             投保人客户ID
 * @param insuredCount         被保险人数
 * @param exactPremium         投保单总保费（Σ 段保费，主险冗余口径见类注释）
 * @param insurancePeriodStart 保障起期（主险段起期）
 * @param insurancePeriodEnd   保障止期（主险段止期）
 * @param insuranceLines       险种段列表（L2，1..N，每段独立保额/保费/期间/缴费/标的）
 * @param underwritingPriority 核保优先级
 * @param insuredPartyList     参与方清单（投保人/被保险人/受益人）
 * @param insuranceType        主险险种三级分类
 * @param collectionMode       收费方式（出单期确定，透传至保单）
 * @param channelInfo          渠道信息（透传至保单）
 * @param bizNo                出单业务流水号（幂等与进度追溯）
 * @param marketPackageId      营销包ID（弱引用，可空）
 * @param createTime           创建时间
 * @param tenantId             租户ID
 * @param sumInsured           主险基本保额（billing 保费计算入参）
 * @param paymentMode          主险缴费模式 code
 * @param premiumPaymentYears  主险缴费年数（0 表示未知）
 */
public record InsuranceCreatedEvent(String insuranceId, String insuranceNo, String proposalId, PolicyForm policyForm,
                                    String holderId, int insuredCount, BigDecimal exactPremium,
                                    LocalDateTime insurancePeriodStart, LocalDateTime insurancePeriodEnd,
                                    List<InsuranceLine> insuranceLines, int underwritingPriority,
                                    InsuredPartyList insuredPartyList, InsuranceProductType insuranceType,
                                    PremiumCollectionMode collectionMode, ChannelInfo channelInfo, String bizNo,
                                    String marketPackageId, LocalDateTime createTime, String tenantId,
                                    BigDecimal sumInsured, String paymentMode, int premiumPaymentYears) {

    /**
     * 险种段的产品编码列表（兼容既有按编码消费的下游：核保请求、保费计算）。
     *
     * @return 产品编码列表；无段时返回空列表
     */
    public List<String> productCodes() {
        if (insuranceLines == null || insuranceLines.isEmpty()) {
            return List.of();
        }
        return insuranceLines.stream().map(InsuranceLine::productCode).filter(code -> code != null).toList();
    }

    /**
     * 主险段（一张投保单有且仅有一个）。
     *
     * @return 主险段；无段时返回 null
     */
    public InsuranceLine mainLine() {
        if (insuranceLines == null) {
            return null;
        }
        return insuranceLines.stream().filter(InsuranceLine::isMain).findFirst().orElse(null);
    }
}
