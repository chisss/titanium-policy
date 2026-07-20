package com.titanium.policy.query.result;

import java.math.BigDecimal;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 保单受益人查询结果
 * <p>
 * 从读模型 {@code t_policy_beneficiary} 查询，作为对外展示契约。
 * </p>
 */
@Data
@NoArgsConstructor
public class PolicyBeneficiaryQueryResult {

    /** 受益人客户ID */
    private String customerId;

    /** 受益人姓名（出单快照） */
    private String beneficiaryName;

    /** 受益类型码（DEATH/SURVIVAL） */
    private String beneficiaryType;

    /** 受益顺位 */
    private Integer orderNo;

    /** 受益份额百分比 */
    private BigDecimal shareRatio;

    /** 保单ID */
    private String policyId;

    /** 租户ID */
    private String tenantId;
}
