package com.titanium.policy.valueobject;

import java.util.List;

import com.titanium.metadata.enums.policy.IssuanceStrategy;
import com.titanium.metadata.enums.product.ProductEnum.IssuanceMode;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.common.enums.IssuanceStage;

/**
 * 出单结果值对象
 * <p>
 * 出单是跨聚合流程，产出物随出单模式而异：一步出单直接产出保单，两步出单先产出投保单，
 * 三步出单先产出意向单。本记录以「当前阶段 + 各单据ID」表达进度，调用方据 {@link #currentStage}
 * 判断后续动作（等待核保 / 去支付 / 已完成）。
 * </p>
 * <p>
 * 保单以<b>列表</b>返回（{@link #policies}）：合并出单策略下长度为 1，拆分出单落地后长度为 N，
 * 契约无需随策略变化而调整。
 * </p>
 *
 * @param success           是否受理成功（业务校验通过并已进入流程）
 * @param bizNo             业务流水号（幂等键，进度查询依据）
 * @param issuanceMode      出单模式（一步/两步/三步，由产品配置决定）
 * @param issuanceStrategy  出单策略（合并/拆分）
 * @param currentStage      当前所处阶段
 * @param proposalId        意向单ID（三步出单）
 * @param proposalNo        意向单编号
 * @param insuranceId       投保单ID（两步/三步出单）
 * @param insuranceNo       投保单编号
 * @param policies          已产出保单列表（合并策略长度 1，拆分策略长度 N）
 * @param underwritingId    核保单ID
 * @param standardPremium   系统试算的标准保费
 * @param extraPremium      核保加费
 * @param payablePremium    应付保费（标准保费 + 加费）
 * @param billId            账单ID（billing 域）
 * @param paymentOrderId    支付单ID（payment 域）
 * @param paymentCredential 支付凭据（线上支付时返回给前端）
 * @param rejectCode        拒绝业务码（受理失败时）
 * @param rejectReason      拒绝原因（受理失败时）
 */
public record IssuanceResult(boolean success, String bizNo, IssuanceMode issuanceMode,
                             IssuanceStrategy issuanceStrategy, IssuanceStage currentStage, String proposalId,
                             String proposalNo, String insuranceId, String insuranceNo, List<IssuedPolicy> policies,
                             String underwritingId, Money standardPremium, Money extraPremium, Money payablePremium,
                             String billId, String paymentOrderId, String paymentCredential, String rejectCode,
                             String rejectReason) {

    /**
     * 构造受理拒绝结果。
     *
     * @param bizNo  业务流水号
     * @param code   拒绝业务码
     * @param reason 拒绝原因（🔴 仅用于领域内部固定文案；面向调用方的文案应走
     *               {@link #rejected(String, RuleDecision)} 以支持多语言渲染）
     * @return 拒绝结果
     */
    public static IssuanceResult rejected(String bizNo, String code, String reason) {
        return new IssuanceResult(false, bizNo, null, null, IssuanceStage.REJECTED, null, null, null, null, List.of(),
                null, null, null, null, null, null, null, code, reason);
    }

    /**
     * 由规则裁决结果构造受理拒绝结果（可国际化路径，首选）。
     * <p>
     * 携带裁决的错误码，文案渲染推迟到边界层按 {@code Accept-Language} 进行。段级违反时
     * 错误码后附段序号（如 {@code 20006011@line2}），便于一单多险场景定位具体险种段。
     * </p>
     *
     * @param bizNo    业务流水号
     * @param decision 规则裁决结果（须为不通过状态）
     * @return 拒绝结果
     */
    public static IssuanceResult rejected(String bizNo, RuleDecision decision) {
        String code = decision.isLineLevel() ? decision.code() + "@line" + decision.lineNo() : decision.code();
        return rejected(bizNo, code, decision.defaultMessage());
    }

    /**
     * 构造意向单已创建结果（三步出单起点，后续由 ProposalIssuanceSaga 接力）。
     *
     * @param bizNo      业务流水号
     * @param proposalId 意向单ID
     * @param proposalNo 意向单编号
     * @return 出单结果
     */
    public static IssuanceResult proposalCreated(String bizNo, String proposalId, String proposalNo) {
        return new IssuanceResult(true, bizNo, IssuanceMode.THREE_STEP, IssuanceStrategy.MERGE_ONE_POLICY,
                IssuanceStage.PROPOSAL_CREATED, proposalId, proposalNo, null, null, List.of(), null, null, null, null,
                null, null, null, null, null);
    }

    /**
     * 构造投保单已创建结果（两步出单起点，后续由 IssuanceSaga 接力核保→承保→出单）。
     *
     * @param bizNo           业务流水号
     * @param insuranceId     投保单ID
     * @param insuranceNo     投保单编号
     * @param standardPremium 试算标准保费
     * @return 出单结果
     */
    public static IssuanceResult insuranceCreated(String bizNo, String insuranceId, String insuranceNo,
                                                  Money standardPremium) {
        return new IssuanceResult(true, bizNo, IssuanceMode.TWO_STEP, IssuanceStrategy.MERGE_ONE_POLICY,
                IssuanceStage.INSURANCE_CREATED, null, null, insuranceId, insuranceNo, List.of(), null,
                standardPremium, null, standardPremium, null, null, null, null, null);
    }

    /**
     * 构造保单已出单结果（一步出单直接完成）。
     *
     * @param bizNo           业务流水号
     * @param policy          已产出保单
     * @param standardPremium 试算标准保费
     * @return 出单结果
     */
    public static IssuanceResult policyIssued(String bizNo, IssuedPolicy policy, Money standardPremium) {
        return new IssuanceResult(true, bizNo, IssuanceMode.ONE_STEP, IssuanceStrategy.MERGE_ONE_POLICY,
                IssuanceStage.POLICY_ISSUED, null, null, null, null, List.of(policy), null, standardPremium, null,
                standardPremium, null, null, null, null, null);
    }

    /**
     * 补充收费信息（收费编排开单后回填）。
     *
     * @param billId            账单ID
     * @param paymentOrderId    支付单ID
     * @param paymentCredential 支付凭据
     * @return 补充后的新实例
     */
    public IssuanceResult withCollection(String billId, String paymentOrderId, String paymentCredential) {
        return new IssuanceResult(success, bizNo, issuanceMode, issuanceStrategy, IssuanceStage.PENDING_COLLECTION,
                proposalId, proposalNo, insuranceId, insuranceNo, policies, underwritingId, standardPremium,
                extraPremium, payablePremium, billId, paymentOrderId, paymentCredential, rejectCode, rejectReason);
    }

    /**
     * 首张保单ID（合并出单策略下即唯一保单）。
     *
     * @return 保单ID；尚未产出保单时返回 null
     */
    public String firstPolicyId() {
        return policies != null && !policies.isEmpty() ? policies.get(0).policyId() : null;
    }

    /**
     * 已产出保单值对象
     *
     * @param policyId     保单ID
     * @param policyNo     保单号
     * @param policyStatus 保单状态码
     * @param lineCount    险种段数量（单险种为 1，一单多险 > 1）
     * @param totalPremium 保单总保费
     */
    public record IssuedPolicy(String policyId, String policyNo, String policyStatus, int lineCount,
                               Money totalPremium) {
    }
}
