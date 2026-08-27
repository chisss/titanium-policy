package com.titanium.policy.web.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.metadata.enums.policy.PolicyForm;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 保单详情响应对象
 * <p>
 * Web 层出参，由 {@code PolicyWebMapper} 从读模型查询结果 {@code PolicyQueryResult} 转换而来，
 * 面向后台/端上展示，表现层不直接返回聚合根或读模型实体。
 * </p>
 */
@Schema(description = "保单详情")
@Data
public class PolicyDetailVO {

    @Schema(description = "保单ID")
    private String                  policyId;

    @Schema(description = "保单号")
    private String                  policyNo;

    @Schema(description = "保单形态")
    private PolicyForm              policyForm;

    @Schema(description = "投保人ID")
    private String                  policyHolderId;

    @Schema(description = "投保人姓名")
    private String                  policyHolderName;

    @Schema(description = "被保险人ID")
    private String                  insuredId;

    @Schema(description = "被保险人姓名")
    private String                  insuredName;

    @Schema(description = "产品编码")
    private String                  productCode;

    @Schema(description = "产品名称")
    private String                  productName;

    @Schema(description = "保额")
    private Double                  sumInsured;

    @Schema(description = "保费")
    private Double                  premium;

    @Schema(description = "生效日期")
    private LocalDateTime           effectiveDate;

    @Schema(description = "过期日期")
    private LocalDateTime           expiryDate;

    @Schema(description = "保单状态")
    private PolicyEnum.PolicyStatus status;

    @Schema(description = "创建时间")
    private LocalDateTime           createTime;

    @Schema(description = "更新时间")
    private LocalDateTime           updateTime;

    @Schema(description = "关联意向单ID")
    private String                  proposalId;

    @Schema(description = "关联核保单ID")
    private String                  underwritingId;

    @Schema(description = "营销包ID")
    private String                  marketPackageId;

    @Schema(description = "主险产品ID")
    private String                  productId;

    @Schema(description = "保单总保费")
    private BigDecimal              totalPremium;

    @Schema(description = "险种段数量")
    private Integer                 lineCount;

    @Schema(description = "等待期届满日")
    private LocalDateTime           waitingPeriodEndDate;

    @Schema(description = "犹豫期届满日")
    private LocalDateTime           hesitationPeriodEndDate;

    @Schema(description = "收费方式码")
    private String                  collectionMode;

    @Schema(description = "收讫状态码")
    private String                  collectionStatus;

    @Schema(description = "已收保费金额")
    private BigDecimal              collectedAmount;

    @Schema(description = "渠道ID")
    private String                  channelId;

    @Schema(description = "销售渠道大类码")
    private String                  salesChannel;

    @Schema(description = "代理人或业务员ID")
    private String                  agentId;
}
