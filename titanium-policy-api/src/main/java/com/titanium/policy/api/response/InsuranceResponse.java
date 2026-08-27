package com.titanium.policy.api.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.metadata.enums.billing.PremiumCollectionMode;
import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.PaymentFrequency;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum.ConclusionType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 投保单对外传输契约（DTO）
 * <p>
 * 跨服务远程调用出参，由 web 层 {@code InsuranceWebMapper} 从读模型查询结果
 * {@code InsuranceQueryResult} 转换而来。api 模块零内部依赖，不得引用 domain 层的
 * {@code InsuranceStatus.StatusCode}，故投保单状态以 {@code String} 承载（Mapper 侧 {@code .name()} 转换）；
 * {@code PolicyForm}/{@code ConclusionType} 属 metadata 公共枚举，可直接使用。
 * </p>
 */
@Schema(description = "投保单DTO")
@Data
public class InsuranceResponse {

    @Schema(description = "投保单ID", example = "INS20260701001")
    private String         insuranceId;

    @Schema(description = "投保单编号", example = "INS20260701001")
    private String         insuranceNo;

    @Schema(description = "关联意向单ID", example = "PROP20260701001")
    private String         proposalId;

    @Schema(description = "保单形态", example = "INDIVIDUAL")
    private PolicyForm     policyForm;

    @Schema(description = "险种三级分类")
    private InsuranceProductType insuranceType;

    @Schema(description = "主险产品ID")
    private String         productId;

    @Schema(description = "主险基本保额")
    private BigDecimal     sumInsured;

    @Schema(description = "主险缴费频率")
    private PaymentFrequency paymentFrequency;

    @Schema(description = "主险缴费年数")
    private Integer        premiumPaymentYears;

    @Schema(description = "收费方式")
    private PremiumCollectionMode collectionMode;

    @Schema(description = "渠道ID")
    private String         channelId;

    @Schema(description = "出单业务流水号")
    private String         bizNo;

    @Schema(description = "营销包ID")
    private String         marketPackageId;

    @Schema(description = "险种段数量")
    private Integer        lineCount;

    @Schema(description = "投保人ID", example = "CUST001")
    private String         holderId;

    @Schema(description = "被保险人数", example = "1")
    private Integer        insuredCount;

    @Schema(description = "精确保费", example = "1200.00")
    private BigDecimal     exactPremium;

    @Schema(description = "币种", example = "CNY")
    private String         currency;

    @Schema(description = "保险起期", example = "2026-07-01T00:00:00")
    private LocalDateTime  insurancePeriodStart;

    @Schema(description = "保险止期", example = "2027-07-01T00:00:00")
    private LocalDateTime  insurancePeriodEnd;

    @Schema(description = "投保单状态", example = "UNDERWRITING")
    private String         status;

    @Schema(description = "核保结论", example = "APPROVED")
    private ConclusionType underwritingResultCode;

    @Schema(description = "核保单号", example = "UW20260701001")
    private String         underwritingId;

    @Schema(description = "承保时间", example = "2026-07-02T10:00:00")
    private LocalDateTime  issuedTime;

    @Schema(description = "创建时间", example = "2026-07-01T00:00:00")
    private LocalDateTime  createTime;

    @Schema(description = "更新时间", example = "2026-07-01T00:00:00")
    private LocalDateTime  updateTime;

    @Schema(description = "租户ID", example = "tenant-001")
    private String         tenantId;
}
