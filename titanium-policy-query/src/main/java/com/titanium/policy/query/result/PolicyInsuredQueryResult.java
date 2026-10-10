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

    /**
     * 被保险人ID（= 读模型行标识 {@code PolicyInsuredView.id}，即保全集合字段的对象标识）。
     * <p>
     * 🔴 成员增减入口（web/api 的 DELETE /insured-members/{insuredId}）以本字段为定位键：
     * 合规成员标识（1-32 位 [A-Za-z0-9._-]）下与聚合内 {@code InsuredInfo.insuredId} 同值，
     * 否则为「保单+顺序」派生值（存量长标识场景，写侧按聚合内标识匹配、届时将移除失败）。
     * </p>
     */
    private String insuredId;

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
