package com.titanium.policy.query.result;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.SalesChannel;
import com.titanium.policy.valueobject.proposal.ProposalStatus;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 投保意向单查询结果
 * <p>
 * 读侧查询返回对象，封装 {@code t_proposal_view} 读模型数据。
 * </p>
 */
@Data
@NoArgsConstructor
public class ProposalQueryResult {

    /** 意向单ID */
    private String                    proposalId;

    /** 意向单编号 */
    private String                    proposalNo;

    /** 保单形态 */
    private PolicyForm                policyForm;

    /** 销售渠道 */
    private SalesChannel              channel;

    /** 客户ID */
    private String                    customerId;

    /** 意向保额 */
    private BigDecimal                intendedSumInsured;

    /** 意向保费 */
    private BigDecimal                intendedPremium;

    /** 保险起期 */
    private LocalDateTime             insurancePeriodStart;

    /** 保险止期 */
    private LocalDateTime             insurancePeriodEnd;

    /** 期望险种编码 */
    private String                    expectedProductCode;

    /** 险种三级分类 */
    private InsuranceProductType      insuranceType;

    /** 出单业务流水号 */
    private String                    bizNo;

    /** 渠道ID */
    private String                    channelId;

    /** 营销包ID */
    private String                    marketPackageId;

    /** 意向险种段数量 */
    private Integer                   lineCount;

    /** 意向单状态 */
    private ProposalStatus.StatusCode status;

    /** 创建时间 */
    private LocalDateTime             createTime;

    /** 更新时间 */
    private LocalDateTime             updateTime;

    /** 租户ID */
    private String                    tenantId;
}
