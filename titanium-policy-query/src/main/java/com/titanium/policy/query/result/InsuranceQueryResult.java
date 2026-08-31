package com.titanium.policy.query.result;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.metadata.enums.billing.PremiumCollectionMode;
import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.PaymentFrequency;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.policy.common.enums.InsuranceStatusCode;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 投保单查询结果
 * <p>
 * 读侧查询返回对象，封装 {@code t_insurance_view} 读模型数据。
 * </p>
 */
@Data
@NoArgsConstructor
public class InsuranceQueryResult {

    /** 投保单ID */
    private String                     insuranceId;

    /** 投保单编号 */
    private String                     insuranceNo;

    /** 关联意向单ID */
    private String                     proposalId;

    /** 保单形态 */
    private PolicyForm                 policyForm;

    /** 险种三级分类 */
    private InsuranceProductType       insuranceType;

    /** 主险产品ID */
    private String                     productId;

    /** 主险基本保额 */
    private BigDecimal                 sumInsured;

    /** 主险缴费频率 */
    private PaymentFrequency           paymentFrequency;

    /** 主险缴费年数 */
    private Integer                    premiumPaymentYears;

    /** 收费方式 */
    private PremiumCollectionMode      collectionMode;

    /** 渠道ID */
    private String                     channelId;

    /** 出单业务流水号 */
    private String                     bizNo;

    /** 营销包ID */
    private String                     marketPackageId;

    /** 险种段数量 */
    private Integer                    lineCount;

    /** 投保人ID */
    private String                     holderId;

    /** 被保险人数 */
    private Integer                    insuredCount;

    /** 精确保费 */
    private BigDecimal                 exactPremium;

    /** 币种 */
    private String                     currency;

    /** 保险起期 */
    private LocalDateTime              insurancePeriodStart;

    /** 保险止期 */
    private LocalDateTime              insurancePeriodEnd;

    /** 投保单状态 */
    private InsuranceStatusCode status;

    /** 核保结论 */
    private ConclusionType             underwritingResultCode;

    /** 核保单号 */
    private String                     underwritingId;

    /** 承保时间 */
    private LocalDateTime              issuedTime;

    /** 创建时间 */
    private LocalDateTime              createTime;

    /** 更新时间 */
    private LocalDateTime              updateTime;

    /** 租户ID */
    private String                     tenantId;
}
