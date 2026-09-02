package com.titanium.policy.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 保单条款快照响应（Feign 契约，下游→上游出参）
 * <p>
 * 记录保单各险种段适用的条款及其版本（签发即冻结）。下游（如 claim 域责任校验 CLAIM-4）
 * 凭 {@code clauseId} 穿透 clause 域取保险责任。
 * </p>
 */
@Schema(description = "保单条款快照响应")
@Data
public class PolicyClauseResponse {

    @Schema(description = "保单ID", example = "P20230801001")
    private String  policyId;

    @Schema(description = "所属险种段ID", example = "line-001")
    private String  policyProductId;

    @Schema(description = "条款ID（指向 clause 域）", example = "clause-001")
    private String  clauseId;

    @Schema(description = "条款编码", example = "CLAUSE-DEATH-001")
    private String  clauseCode;

    @Schema(description = "条款名称", example = "身故保险金条款")
    private String  clauseName;

    @Schema(description = "条款版本（签发即冻结）", example = "V1.0")
    private String  clauseVersion;

    @Schema(description = "是否主条款", example = "true")
    private Boolean mainClause;
}
