package com.titanium.policy.web.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.SalesChannel;
import com.titanium.policy.common.enums.ProposalStatusCode;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 投保意向单响应对象
 * <p>
 * Web 层出参，由 {@code ProposalWebMapper} 从读模型查询结果 {@code ProposalQueryResult} 转换而来，
 * 表现层不直接返回聚合根。
 * </p>
 */
@Schema(description = "投保意向单详情")
@Data
public class ProposalVO {

    @Schema(description = "意向单ID")
    private String                    proposalId;

    @Schema(description = "意向单编号")
    private String                    proposalNo;

    @Schema(description = "保单形态")
    private PolicyForm                policyForm;

    @Schema(description = "销售渠道")
    private SalesChannel              channel;

    @Schema(description = "客户ID")
    private String                    customerId;

    @Schema(description = "意向保额")
    private BigDecimal                intendedSumInsured;

    @Schema(description = "意向保费")
    private BigDecimal                intendedPremium;

    @Schema(description = "保险起期")
    private LocalDateTime             insurancePeriodStart;

    @Schema(description = "保险止期")
    private LocalDateTime             insurancePeriodEnd;

    @Schema(description = "期望险种编码")
    private String                    expectedProductCode;

    @Schema(description = "险种三级分类")
    private InsuranceProductType      insuranceType;

    @Schema(description = "出单业务流水号")
    private String                    bizNo;

    @Schema(description = "渠道ID")
    private String                    channelId;

    @Schema(description = "营销包ID")
    private String                    marketPackageId;

    @Schema(description = "意向险种段数量")
    private Integer                   lineCount;

    @Schema(description = "意向单状态")
    private ProposalStatusCode status;

    @Schema(description = "创建时间")
    private LocalDateTime             createTime;

    @Schema(description = "更新时间")
    private LocalDateTime             updateTime;
}
