package com.titanium.policy.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.titanium.metadata.enums.policy.PolicyForm;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 意向单转投保单传输契约（DTO）
 * <p>
 * 跨服务远程发起「投保意向单转投保单」的入参，字段对齐领域命令
 * {@code ConvertProposalToInsuranceCommand}（金额以 {@code BigDecimal}+币种平铺、无聚合根标识注解）。
 * api 模块仅依赖 metadata 公共枚举（{@code PolicyForm}），
 * 金额以 {@code BigDecimal}+{@code currency} 平铺承载。
 * </p>
 */
@Schema(description = "意向单转投保单DTO")
@Data
public class ConvertToInsuranceDTO {

    @Schema(description = "投保单ID", example = "INS20260701001")
    private String        insuranceId;

    @Schema(description = "投保单编号", example = "INS20260701001")
    private String        insuranceNo;

    @Schema(description = "关联意向单ID", example = "PROP20260701001")
    private String        proposalId;

    @Schema(description = "保单形态", example = "INDIVIDUAL")
    private PolicyForm    policyForm;

    @Schema(description = "投保人ID", example = "CUST001")
    private String        applicantId;

    @Schema(description = "被保险人数", example = "1")
    private int           insuredCount;

    @Schema(description = "精确保费", example = "1200.00")
    private BigDecimal    exactPremium;

    @Schema(description = "币种", example = "CNY")
    private String        currency;

    @Schema(description = "保险起期", example = "2026-07-01T00:00:00")
    private LocalDateTime insurancePeriodStart;

    @Schema(description = "保险止期", example = "2027-07-01T00:00:00")
    private LocalDateTime insurancePeriodEnd;

    @Schema(description = "险种编码列表")
    private List<String>  productCodes;

    @Schema(description = "核保优先级", example = "0")
    private int           underwritingPriority;

    @Schema(description = "转换原因", example = "客户确认投保")
    private String        changeReason;
}
