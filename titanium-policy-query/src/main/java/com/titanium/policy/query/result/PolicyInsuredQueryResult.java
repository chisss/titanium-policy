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

    /** 被保险人证件类型快照 */
    private String idType;

    /** 被保险人证件号码快照 */
    private String idNo;

    /** 被保险人年龄快照 */
    private Integer age;

    /** 被保险人性别快照 */
    private String gender;

    /** 被保险人手机号快照 */
    private String phone;

    /** 与投保人关系快照 */
    private String relationToHolder;

    /** 家庭成员关系码（家庭险专属） */
    private String familyRelation;

    /** 保单ID */
    private String policyId;

    /** 租户ID */
    private String tenantId;
}
