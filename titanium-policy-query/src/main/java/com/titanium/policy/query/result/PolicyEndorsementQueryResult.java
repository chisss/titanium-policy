package com.titanium.policy.query.result;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 保单批改历史查询结果
 * <p>
 * 从读模型 {@code t_policy_endorsement_view} 查询，作为对外展示契约。
 * </p>
 */
@Data
@NoArgsConstructor
public class PolicyEndorsementQueryResult {

    /** 批单号 */
    private String        endorsementNo;

    /** 保单ID */
    private String        policyId;

    /** 批改类型编码 */
    private String        updateType;

    /** 批改大类编码 */
    private String        category;

    /** 批改后保单版本号 */
    private int           policyVersion;

    /** 批单生效日 */
    private LocalDateTime effectiveDate;

    /** 变更摘要 */
    private String        changeSummary;

    /** 是否触发保费重算 */
    private boolean       requiresPremiumRecalc;

    /** 来源保全案件ID */
    private String        sourceMaintenanceId;

    /** 操作人 */
    private String        operatorId;

    /** 批改落地时间 */
    private LocalDateTime endorsedAt;

    /** 租户ID */
    private String        tenantId;
}
