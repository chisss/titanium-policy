package com.titanium.policy.query.result;

import java.time.LocalDateTime;

/**
 * 保单侧已生效保全案件引用。
 * <p>
 * 该结果仅由本域批改投影派生，不代表保全案件当前流程状态；在途案件仍应从保全管理页面查询。
 * </p>
 */
public record PolicyMaintenanceCaseReferenceQueryResult(
        String maintenanceId,
        String endorsementNo,
        String updateType,
        int policyVersion,
        LocalDateTime effectiveDate,
        LocalDateTime endorsedAt) {
}
