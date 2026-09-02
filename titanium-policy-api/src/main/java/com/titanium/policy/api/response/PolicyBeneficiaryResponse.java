package com.titanium.policy.api.response;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 保单受益人响应（Feign 契约，下游→上游出参）
 * <p>
 * 保单受益人主数据镜像，下游（如 claim 域身故给付 CLAIM-4）据此比对受益人身份与顺位：
 * 按受益顺位分配给付（第一顺位优先），拒绝未知受益人。
 * </p>
 */
@Schema(description = "保单受益人响应")
@Data
public class PolicyBeneficiaryResponse {

    @Schema(description = "受益人ID", example = "B20230801001")
    private String     beneficiaryId;

    @Schema(description = "客户ID（受益人已建客户档案时）", example = "C20230801001")
    private String     customerId;

    @Schema(description = "受益人姓名", example = "张三")
    private String     beneficiaryName;

    @Schema(description = "证件类型", example = "ID_CARD")
    private String     idType;

    @Schema(description = "证件号码", example = "110101199001011234")
    private String     idNo;

    @Schema(description = "性别", example = "MALE")
    private String     gender;

    @Schema(description = "手机号", example = "13800138000")
    private String     phone;

    @Schema(description = "受益人类型", example = "LEGAL")
    private String     beneficiaryType;

    @Schema(description = "受益顺位（1 起，第一顺位优先）", example = "1")
    private Integer    orderNo;

    @Schema(description = "登记份额（百分比数值，如 50 表示 50%）", example = "50")
    private BigDecimal shareRatio;

    @Schema(description = "保单ID", example = "P20230801001")
    private String     policyId;

    @Schema(description = "租户ID", example = "tenant-001")
    private String     tenantId;
}
