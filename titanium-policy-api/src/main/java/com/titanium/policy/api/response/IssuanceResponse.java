package com.titanium.policy.api.response;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 出单响应（Feign 契约）
 * <p>
 * 出单产出物随出单模式而异：一步出单直接产出保单，两步出单先产出投保单，三步出单先产出意向单。
 * 调用方据 {@link #currentStage} 判断后续动作——待收费则引导支付、核保中则轮询、已出单则展示保单。
 * </p>
 * <p>
 * 保单以<b>列表</b>返回：合并出单策略下长度为 1，拆分出单落地后长度为 N，契约无需随策略变化调整。
 * </p>
 */
@Schema(description = "出单响应")
@Data
public class IssuanceResponse {

    @Schema(description = "是否受理成功（业务校验通过并已进入流程）", example = "true")
    private Boolean             success;

    @Schema(description = "业务流水号（幂等键，进度查询依据）", example = "BIZ20260807001")
    private String              bizNo;

    @Schema(description = "出单模式码（ONE_STEP/TWO_STEP/THREE_STEP，由产品配置决定）", example = "TWO_STEP")
    private String              issuanceMode;

    @Schema(description = "出单策略码（MERGE_ONE_POLICY/SPLIT_MULTI_POLICY）", example = "MERGE_ONE_POLICY")
    private String              issuanceStrategy;

    @Schema(description = "当前阶段码（ACCEPTED/VALIDATING/QUOTING/PROPOSAL_CREATED/INSURANCE_CREATED/"
            + "UNDERWRITING/PENDING_COLLECTION/POLICY_ISSUED/POLICY_EFFECTIVE/REJECTED）",
            example = "INSURANCE_CREATED")
    private String              currentStage;

    @Schema(description = "意向单ID（三步出单）", example = "PROP-001")
    private String              proposalId;

    @Schema(description = "意向单编号")
    private String              proposalNo;

    @Schema(description = "投保单ID（两步/三步出单）", example = "INS-001")
    private String              insuranceId;

    @Schema(description = "投保单编号")
    private String              insuranceNo;

    @Schema(description = "已产出保单列表（合并策略长度 1，拆分策略长度 N）")
    private List<IssuedPolicy>  policies;

    @Schema(description = "核保单ID")
    private String              underwritingId;

    @Schema(description = "系统试算的标准保费")
    private BigDecimal          standardPremium;

    @Schema(description = "核保加费")
    private BigDecimal          extraPremium;

    @Schema(description = "应付保费（标准保费 + 加费）")
    private BigDecimal          payablePremium;

    @Schema(description = "账单ID（billing 域）")
    private String              billId;

    @Schema(description = "支付单ID（payment 域）")
    private String              paymentOrderId;

    @Schema(description = "支付凭据（线上支付时返回给前端）")
    private String              paymentCredential;

    @Schema(description = "拒绝业务码（受理失败时）", example = "ISSUANCE_RISK_REJECTED")
    private String              rejectCode;

    @Schema(description = "拒绝原因（受理失败时，含违反的具体规则与所在险种段）")
    private String              rejectReason;

    /**
     * 已产出保单
     */
    @Schema(description = "已产出保单")
    @Data
    public static class IssuedPolicy {

        @Schema(description = "保单ID", example = "POL-001")
        private String     policyId;

        @Schema(description = "保单号", example = "POL-2026-0001")
        private String     policyNo;

        @Schema(description = "保单状态码（NOT_EFFECTIVE/EFFECTIVE 等）", example = "NOT_EFFECTIVE")
        private String     policyStatus;

        @Schema(description = "险种段数量（单险种为 1，一单多险 > 1）", example = "3")
        private Integer    lineCount;

        @Schema(description = "保单总保费（= Σ 计入段的保费，拒保段已剔除）")
        private BigDecimal totalPremium;
    }
}
