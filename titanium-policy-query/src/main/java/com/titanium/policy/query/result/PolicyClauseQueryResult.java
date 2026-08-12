package com.titanium.policy.query.result;

import lombok.Data;

/**
 * 保单条款快照查询结果（L2.5，读侧对外契约）
 * <p>
 * 记录保单各险种段适用的条款及其版本。查看条款全文或免责条款时经 {@link #clauseId} 穿透至
 * clause 域。
 * </p>
 */
@Data
public class PolicyClauseQueryResult {

    /** 保单ID */
    private String  policyId;

    /** 所属险种段ID */
    private String  policyProductId;

    /** 条款ID（指向 clause 域） */
    private String  clauseId;

    /** 条款编码 */
    private String  clauseCode;

    /** 条款名称 */
    private String  clauseName;

    /** 条款版本（签发即冻结） */
    private String  clauseVersion;

    /** 是否主条款 */
    private Boolean mainClause;
}
