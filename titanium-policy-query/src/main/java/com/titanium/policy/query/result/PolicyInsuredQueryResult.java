package com.titanium.policy.query.result;


import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 保单被保险人查询结果
 * <p>
 * 从读模型 {@code t_policy_insured} 查询，作为对外展示契约。
 * </p>
 */
@Data
@NoArgsConstructor
public class PolicyInsuredQueryResult {

    /** 被保险人客户ID */
    private String customerId;

    /** 被保险人姓名（出单快照） */
    private String insuredName;

    /** 家庭成员关系码（家庭险专属） */
    private String familyRelation;

    /** 保单ID */
    private String policyId;

    /** 租户ID */
    private String tenantId;
}
