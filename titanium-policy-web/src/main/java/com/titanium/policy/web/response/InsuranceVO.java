package com.titanium.policy.web.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.metadata.enums.billing.PremiumCollectionMode;
import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.PaymentFrequency;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;
import com.titanium.policy.common.enums.InsuranceStatusCode;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 投保单响应对象
 * <p>
 * Web 层出参，由 {@code InsuranceWebMapper} 从读模型查询结果 {@code InsuranceQueryResult} 转换而来，
 * 表现层不直接返回聚合根。
 * </p>
 */
@Schema(description = "投保单详情")
@Data
public class InsuranceVO {

    @Schema(description = "投保单ID")
    private String                     insuranceId;

    @Schema(description = "投保单编号")
    private String                     insuranceNo;

    @Schema(description = "关联意向单ID")
    private String                     proposalId;

    @Schema(description = "保单形态")
    private PolicyForm                 policyForm;

    @Schema(description = "险种三级分类")
    private InsuranceProductType       insuranceType;

    @Schema(description = "主险产品ID")
    private String                     productId;

    @Schema(description = "主险基本保额")
    private BigDecimal                 sumInsured;

    @Schema(description = "主险缴费频率")
    private PaymentFrequency           paymentFrequency;

    @Schema(description = "主险缴费年数")
    private Integer                    premiumPaymentYears;

    @Schema(description = "收费方式")
    private PremiumCollectionMode      collectionMode;

    @Schema(description = "渠道ID")
    private String                     channelId;

    @Schema(description = "出单业务流水号")
    private String                     bizNo;

    @Schema(description = "营销包ID")
    private String                     marketPackageId;

    @Schema(description = "险种段数量")
    private Integer                    lineCount;

    @Schema(description = "投保人ID")
    private String                     holderId;

    @Schema(description = "被保险人数")
    private Integer                    insuredCount;

    @Schema(description = "精确保费")
    private BigDecimal                 exactPremium;

    @Schema(description = "币种")
    private String                     currency;

    @Schema(description = "保险起期")
    private LocalDateTime              insurancePeriodStart;

    @Schema(description = "保险止期")
    private LocalDateTime              insurancePeriodEnd;

    @Schema(description = "投保单状态")
    private InsuranceStatusCode status;

    @Schema(description = "核保结论")
    private ConclusionType             underwritingResultCode;

    @Schema(description = "核保单号")
    private String                     underwritingId;

    @Schema(description = "承保时间")
    private LocalDateTime              issuedTime;

    @Schema(description = "创建时间")
    private LocalDateTime              createTime;

    @Schema(description = "更新时间")
    private LocalDateTime              updateTime;
}
