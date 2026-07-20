package com.titanium.policy.web.dto;

import java.time.LocalDateTime;

import com.titanium.policy.common.enums.PolicyDataUpdateType;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 申请保单批改请求（数据/要素类批改回写）
 * <p>
 * 承载批单号、批改类型、批改生效日、变更摘要与原始快照等，经 {@code PolicyWebMapper} 转换为
 * {@code ApplyPolicyEndorsementCommand}，操作人/租户ID 取请求头。
 * </p>
 */
@Schema(description = "申请保单批改请求")
@Data
public class ApplyEndorsementDTO {

    @Schema(description = "批单号", example = "END20260801001")
    private String               endorsementNo;

    @Schema(description = "批改类型", example = "POLICY_HOLDER_INFO")
    private PolicyDataUpdateType updateType;

    @Schema(description = "批改生效日", example = "2026-08-01T00:00:00")
    private LocalDateTime        endorsementEffectiveDate;

    @Schema(description = "变更摘要", example = "变更投保人联系方式")
    private String               changeSummary;

    @Schema(description = "变更前快照（JSON）")
    private String               originalSnapshot;

    @Schema(description = "来源保全ID", example = "MNT20260801001")
    private String               sourceMaintenanceId;
}
