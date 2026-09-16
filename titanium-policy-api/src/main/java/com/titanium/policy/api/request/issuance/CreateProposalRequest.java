package com.titanium.policy.api.request.issuance;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.metadata.enums.insurance.InsuranceProductType;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.SalesChannel;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 创建投保意向单请求（跨域集成远程创建入参）
 * <p>
 * 由 web 层 {@code ProposalApiProvider} 经 {@code ProposalWebMapper} 转换为领域命令
 * {@code CreateProposalCommand} 后调用应用层门面。
 * </p>
 * <p>
 * 🔴 <b>本契约只承载意向单的标量头字段（草稿级），不承载参与方与标的</b>：相比
 * {@code CreateProposalCommand} 的完整形态，此处<b>没有</b> {@code insuredPartyList}（投保人/被保险人/受益人）、
 * {@code proposalSubjects}（标的清单）与 {@code proposalLines}，命令兼容构造器相应位置一律置 {@code null}。
 * </p>
 * <p>
 * 🔴 <b>由此契约创建的意向单恒不可提交</b>：{@code PUT /api/v1/proposals/{proposalId}/submit} 的前置是
 * 「至少一名申请人 + 至少一个标的」，而缺上述字段时聚合内两张清单恒为空表，
 * 且命令层与聚合层均无参与方/标的补录入口（{@code Proposal#addApplicant}/{@code addSubject} 为无命令封装的纯对象方法）。
 * </p>
 * <p>
 * ✅ <b>完整出单（含参与方与标的装配并自动提交）请走统一出单入口</b>
 * {@code POST /api/v1/issuances}（{@code PolicyIssuanceApi}），其装配器确会填充
 * {@code insuredPartyList} 与 {@code proposalSubjects}，并由 {@code IssuanceOrchestrator} 自动发
 * {@code SubmitProposalCommand}。本契约的定位是草稿创建，不是出单链路。
 * </p>
 */
@Schema(description = "创建投保意向单请求（草稿级：不含参与方与标的，不可提交）")
@Data
public class CreateProposalRequest {

    @Schema(description = "意向单ID", example = "PROP20260701001")
    private String        proposalId;

    @Schema(description = "意向单编号", example = "PRP20260701001")
    private String        proposalNo;

    @Schema(description = "保单形态", example = "INDIVIDUAL")
    private PolicyForm    policyForm;

    @Schema(description = "销售渠道", example = "DIRECT")
    private SalesChannel  channel;

    @Schema(description = "客户ID", example = "CUST001")
    private String        customerId;

    @Schema(description = "意向保额", example = "100000.00")
    private BigDecimal    intendedSumInsured;

    @Schema(description = "意向保费", example = "1200.00")
    private BigDecimal    intendedPremium;

    @Schema(description = "币种", example = "CNY")
    private String        currency;

    @Schema(description = "保险起期", example = "2026-07-01T00:00:00")
    private LocalDateTime insurancePeriodStart;

    @Schema(description = "保险止期", example = "2027-07-01T00:00:00")
    private LocalDateTime insurancePeriodEnd;

    @Schema(description = "期望险种编码", example = "PROD_A")
    private String        expectedProductCode;

    @Schema(description = "险种三级分类(可空)", example = "TERM_LIFE")
    private InsuranceProductType insuranceType;
}
