package com.titanium.policy.api.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.SalesChannel;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 投保意向单 DTO（跨域集成远程传输契约）
 * <p>
 * 状态字段以 {@code String} 承载：意向单状态枚举 {@code ProposalStatus.StatusCode} 位于
 * domain 层，api 模块零内部领域依赖（仅依赖 metadata/feign/swagger），故不引入领域枚举，
 * 由 web 层 {@code ProposalWebMapper} 以 {@code .name()} 转换。
 * </p>
 */
@Schema(description = "投保意向单DTO")
@Data
public class ProposalResponse {

    @Schema(description = "意向单ID", example = "PROP20260701001")
    private String        proposalId;

    @Schema(description = "意向单编号", example = "PRP20260701001")
    private String        proposalNo;

    @Schema(description = "保单形态", example = "INDIVIDUAL")
    private PolicyForm    policyForm;

    @Schema(description = "销售渠道", example = "DIRECT")
    private SalesChannel  channel;

    @Schema(description = "客户ID", example = "CUST001")
    private String        customerId;

    @Schema(description = "意向保额", example = "100000.00")
    private BigDecimal    intendedSumInsured;

    @Schema(description = "意向保费", example = "1200.00")
    private BigDecimal    intendedPremium;

    @Schema(description = "保险起期", example = "2026-07-01T00:00:00")
    private LocalDateTime insurancePeriodStart;

    @Schema(description = "保险止期", example = "2027-07-01T00:00:00")
    private LocalDateTime insurancePeriodEnd;

    @Schema(description = "期望险种编码", example = "PROD_A")
    private String        expectedProductCode;

    @Schema(description = "险种三级分类")
    private InsuranceProductType insuranceType;

    @Schema(description = "出单业务流水号")
    private String        bizNo;

    @Schema(description = "渠道ID")
    private String        channelId;

    @Schema(description = "营销包ID")
    private String        marketPackageId;

    @Schema(description = "意向险种段数量")
    private Integer       lineCount;

    @Schema(description = "意向单状态", example = "DRAFT")
    private String        status;

    @Schema(description = "创建时间", example = "2026-07-01T00:00:00")
    private LocalDateTime createTime;

    @Schema(description = "更新时间", example = "2026-07-01T00:00:00")
    private LocalDateTime updateTime;

    @Schema(description = "租户ID", example = "tenant-001")
    private String        tenantId;
}
