package com.titanium.policy.valueobject.policy;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 条款快照值对象（L2.5）
 * <p>
 * 出单时点对条款域 {@code Clause} 聚合的不可变快照，冻结「本保单适用哪一版条款」这一事实。
 * 条款域后续修订版本（{@code ClauseRevisedEvent} 产生新聚合）不影响已签发保单——
 * 符合「保险合同以订立时条款为准」的法律原则。
 * </p>
 * <p>
 * {@code clauseId} 同时是指向条款域的指针，需要查看条款全文或免责条款时经该 ID 穿透查询；
 * 编码与名称为展示用冗余快照。
 * </p>
 *
 * @param clauseId      条款ID（指向 clause 域聚合根）
 * @param clauseCode    条款编码（快照）
 * @param clauseName    条款名称（快照）
 * @param clauseVersion 条款版本（快照，锁定具体版本）
 * @param isMainClause  是否主条款（一个险种段仅一个主条款，其余为附加条款）
 */
public record ClauseSnapshot(String clauseId, String clauseCode, String clauseName, String clauseVersion,
                             boolean isMainClause) {

    /**
     * 条款快照是否有效（具备可溯源的条款标识）。
     *
     * @return 条款ID非空返回 {@code true}
     */
    @JsonIgnore
    public boolean isValid() {
        return clauseId != null && !clauseId.isBlank();
    }
}
