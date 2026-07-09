package com.titanium.policy.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.SalesChannel;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 创建投保意向单 DTO（跨域集成远程创建入参）
 * <p>
 * 字段对齐领域命令 {@code CreateProposalCommand}，由 web 层 {@code ProposalApiProvider}
 * 经 {@code ProposalWebMapper} 转换为领域命令 {@code CreateProposalCommand} 后调用应用层门面。
 * </p>
 */
@Schema(description = "创建投保意向单DTO")
@Data
public class CreateProposalDTO {

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

    @Schema(description = "币种", example = "CNY")
    private String        currency;

    @Schema(description = "保险起期", example = "2026-07-01T00:00:00")
    private LocalDateTime insurancePeriodStart;

    @Schema(description = "保险止期", example = "2027-07-01T00:00:00")
    private LocalDateTime insurancePeriodEnd;

    @Schema(description = "期望险种编码", example = "PROD_A")
    private String        expectedProductCode;
}
