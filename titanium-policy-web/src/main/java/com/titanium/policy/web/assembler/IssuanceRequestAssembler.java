package com.titanium.policy.web.assembler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.titanium.metadata.enums.BaseEnum;
import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.billing.PremiumCollectionMode;
import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;
import com.titanium.metadata.enums.customer.CustomerEnum.IdCardType;
import com.titanium.metadata.enums.insurance.SubjectType;
import com.titanium.metadata.enums.policy.BeneficiaryType;
import com.titanium.metadata.enums.policy.IssuanceStrategy;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.PaymentFrequency;
import com.titanium.metadata.enums.product.ProductEnum.PeriodUnit;
import com.titanium.metadata.enums.product.ProductEnum.ProductCategory;
import com.titanium.metadata.enums.product.ProductEnum.SalesChannel;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.api.request.SubmitIssuanceRequest;
import com.titanium.policy.api.response.IssuanceResponse;
import com.titanium.policy.common.enums.FamilyRelation;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.valueobject.IssuancePlanLine;
import com.titanium.policy.valueobject.IssuanceRequest;
import com.titanium.policy.valueobject.IssuanceResult;

/**
 * 出单请求装配器（web 层协议转换）
 * <p>
 * 把 HTTP/Feign 契约（{@link SubmitIssuanceRequest}，枚举以 String 承载、金额为裸
 * {@code BigDecimal}）翻译为领域侧出单请求（{@link IssuanceRequest}，强类型枚举 + {@code Money}
 * 值对象 + 结构化参与方清单），以及把出单结果翻译回响应。
 * </p>
 * <p>
 * 🔴 <b>为何不用 MapStruct</b>：本装配含三类声明式映射无法表达的逻辑——① 枚举 code → 枚举的
 * 空安全解析（15 处）；② 扁平参与方列表 → 嵌套 {@link InsuredPartyList} 的结构重组（投保人/
 * 被保险人/受益人三类合一）；③ 受益份额百分比（100）→ 比例（1.0）的量纲换算。属规约允许的
 * {@code XxxAssembler}（复杂对象组装专类），非「伪 MapStruct」。
 * </p>
 */
@Component
public class IssuanceRequestAssembler {

    /** 缺省币种 */
    private static final String DEFAULT_CURRENCY = CurrencyEnum.CNY.getCode();
    /** 受益份额百分比 → 比例的换算基数（契约传 100 表示 100%，领域侧以 1.0 表示） */
    private static final BigDecimal RATIO_BASE = new BigDecimal("100");

    /**
     * 契约请求 → 领域出单请求。
     *
     * @param request  出单请求契约
     * @param tenantId 租户ID（请求头透传）
     * @return 领域出单请求
     */
    public IssuanceRequest toDomainRequest(SubmitIssuanceRequest request, String tenantId) {
        String currency = request.getCurrency() != null ? request.getCurrency() : DEFAULT_CURRENCY;
        return new IssuanceRequest(request.getBizNo(), tenantId, request.getUserId(), request.getMarketPackageId(),
                parseStrategy(request.getIssuanceStrategy()), holderCustomerId(request),
                toInsuredPartyList(request), parsePolicyForm(request.getPolicyForm()), null, request.getPeriodStart(),
                request.getPeriodEnd(), parseCollectionMode(request.getCollectionMode()), request.getChannelId(),
                parseSalesChannel(request.getSalesChannel()), request.getAgentId(),
                toPlanLines(request, currency), toMoney(request.getQuotedPremium(), currency),
                request.getExtendData());
    }

    /**
     * 出单结果 → 契约响应。
     *
     * @param result 出单结果
     * @return 出单响应
     */
    public IssuanceResponse toResponse(IssuanceResult result) {
        IssuanceResponse response = new IssuanceResponse();
        response.setSuccess(result.success());
        response.setBizNo(result.bizNo());
        response.setIssuanceMode(result.issuanceMode() != null ? result.issuanceMode().getCode() : null);
        response.setIssuanceStrategy(result.issuanceStrategy() != null ? result.issuanceStrategy().getCode() : null);
        response.setCurrentStage(result.currentStage() != null ? result.currentStage().getCode() : null);
        response.setProposalId(result.proposalId());
        response.setProposalNo(result.proposalNo());
        response.setInsuranceId(result.insuranceId());
        response.setInsuranceNo(result.insuranceNo());
        response.setPolicies(toPolicyResponses(result));
        response.setUnderwritingId(result.underwritingId());
        response.setStandardPremium(amount(result.standardPremium()));
        response.setExtraPremium(amount(result.extraPremium()));
        response.setPayablePremium(amount(result.payablePremium()));
        response.setBillId(result.billId());
        response.setPaymentOrderId(result.paymentOrderId());
        response.setPaymentCredential(result.paymentCredential());
        response.setRejectCode(result.rejectCode());
        response.setRejectReason(result.rejectReason());
        return response;
    }

    /**
     * 投保人客户ID（有 customerId 直接取，否则为空由 customer 域 upsert 后回填）。
     */
    private String holderCustomerId(SubmitIssuanceRequest request) {
        return request.getHolder() != null ? request.getHolder().getCustomerId() : null;
    }

    /**
     * 扁平参与方列表 → 嵌套参与方清单（投保人 + 被保险人清单 + 受益人清单）。
     */
    private InsuredPartyList toInsuredPartyList(SubmitIssuanceRequest request) {
        SubmitIssuanceRequest.PartyInput holderInput = request.getHolder();
        InsuredPartyList.HolderInfo holder = holderInput != null
                ? new InsuredPartyList.HolderInfo(holderInput.getCustomerId(),
                        holderInput.getCustomerId() != null ? holderInput.getCustomerId() : UUID.randomUUID().toString(),
                        holderInput.getName(), parseCertType(holderInput.getCertType()), holderInput.getCertNo(),
                        holderInput.getMobile())
                : null;

        List<InsuredPartyList.InsuredInfo> insuredList = new ArrayList<>();
        if (request.getInsuredList() != null) {
            for (SubmitIssuanceRequest.PartyInput input : request.getInsuredList()) {
                insuredList.add(new InsuredPartyList.InsuredInfo(input.getCustomerId(),
                        input.getCustomerId() != null ? input.getCustomerId() : UUID.randomUUID().toString(),
                        input.getName(), parseCertType(input.getCertType()), input.getCertNo(),
                        input.getAge() != null ? input.getAge() : 0, parseGender(input.getGender()), input.getMobile(),
                        input.getRelationToHolder(), parseFamilyRelation(input.getFamilyRelation())));
            }
        }

        List<InsuredPartyList.BeneficiaryInfo> beneficiaryList = new ArrayList<>();
        if (request.getBeneficiaryList() != null) {
            for (SubmitIssuanceRequest.PartyInput input : request.getBeneficiaryList()) {
                beneficiaryList.add(new InsuredPartyList.BeneficiaryInfo(input.getCustomerId(),
                        input.getCustomerId() != null ? input.getCustomerId() : UUID.randomUUID().toString(),
                        input.getName(), parseCertType(input.getCertType()), input.getCertNo(),
                        parseGender(input.getGender()), input.getMobile(),
                        parseBeneficiaryType(input.getBeneficiaryType()),
                        input.getBeneficiaryOrder() != null ? input.getBeneficiaryOrder() : 1,
                        toRatio(input.getShareRatio())));
            }
        }
        return new InsuredPartyList(UUID.randomUUID().toString(), holder, insuredList, beneficiaryList);
    }

    /**
     * 契约方案行 → 领域方案行。
     */
    private List<IssuancePlanLine> toPlanLines(SubmitIssuanceRequest request, String currency) {
        if (request.getPlanLines() == null) {
            return List.of();
        }
        List<IssuancePlanLine> lines = new ArrayList<>();
        for (SubmitIssuanceRequest.PlanLine input : request.getPlanLines()) {
            lines.add(new IssuancePlanLine(input.getLineNo() != null ? input.getLineNo() : 1, input.getProductId(),
                    parseProductCategory(input.getProductCategory()), input.getParentLineNo(),
                    toMoney(input.getSumInsured(), currency), input.getCoveragePeriodValue(),
                    parsePeriodUnit(input.getCoveragePeriodUnit()), parseFrequency(input.getPaymentFrequency()),
                    input.getPremiumPaymentYears(), toSubjects(input, currency), input.getExtendData()));
        }
        return List.copyOf(lines);
    }

    /**
     * 契约标的 → 领域标的意图。
     */
    private List<IssuancePlanLine.SubjectIntent> toSubjects(SubmitIssuanceRequest.PlanLine line, String currency) {
        if (line.getSubjects() == null || line.getSubjects().isEmpty()) {
            return List.of();
        }
        List<IssuancePlanLine.SubjectIntent> subjects = new ArrayList<>();
        for (SubmitIssuanceRequest.SubjectInput input : line.getSubjects()) {
            subjects.add(new IssuancePlanLine.SubjectIntent(parseSubjectType(input.getSubjectType()),
                    input.getCustomerId(), input.getSubjectName(), toMoney(input.getSubjectSumInsured(), currency),
                    input.getRelationToHolder(), input.getAttributes()));
        }
        return List.copyOf(subjects);
    }

    /**
     * 出单结果的保单列表 → 响应保单列表。
     */
    private List<IssuanceResponse.IssuedPolicy> toPolicyResponses(IssuanceResult result) {
        if (result.policies() == null || result.policies().isEmpty()) {
            return List.of();
        }
        List<IssuanceResponse.IssuedPolicy> policies = new ArrayList<>();
        for (IssuanceResult.IssuedPolicy issued : result.policies()) {
            IssuanceResponse.IssuedPolicy policy = new IssuanceResponse.IssuedPolicy();
            policy.setPolicyId(issued.policyId());
            policy.setPolicyNo(issued.policyNo());
            policy.setPolicyStatus(issued.policyStatus());
            policy.setLineCount(issued.lineCount());
            policy.setTotalPremium(amount(issued.totalPremium()));
            policies.add(policy);
        }
        return List.copyOf(policies);
    }

    /**
     * 受益份额百分比（100）→ 比例（1.0）。
     */
    private double toRatio(BigDecimal shareRatio) {
        if (shareRatio == null) {
            return 0d;
        }
        return shareRatio.divide(RATIO_BASE, 6, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 数值 + 币种 → 金额值对象（空安全）。
     */
    private Money toMoney(BigDecimal value, String currency) {
        return value != null ? Money.of(value, currency) : null;
    }

    /**
     * 金额值对象 → 数值（空安全）。
     */
    private BigDecimal amount(Money money) {
        return money != null ? money.value() : null;
    }

    // ==================== 枚举 code → 枚举（全部空安全） ====================

    private IssuanceStrategy parseStrategy(String code) {
        return code != null ? IssuanceStrategy.fromCode(code) : IssuanceStrategy.MERGE_ONE_POLICY;
    }

    private PolicyForm parsePolicyForm(String code) {
        return code != null ? PolicyForm.fromCode(code) : null;
    }

    private PremiumCollectionMode parseCollectionMode(String code) {
        return code != null ? PremiumCollectionMode.fromCode(code) : null;
    }

    private SalesChannel parseSalesChannel(String code) {
        return code != null ? SalesChannel.fromCode(code) : null;
    }

    private ProductCategory parseProductCategory(String code) {
        return code != null ? ProductCategory.fromCode(code) : ProductCategory.MAIN;
    }

    private PeriodUnit parsePeriodUnit(String code) {
        return code != null ? PeriodUnit.fromCode(code) : null;
    }

    /**
     * 缴费频率 code → 枚举。
     * <p>
     * {@code PaymentFrequency} 未提供静态 {@code fromCode}，走 {@link BaseEnum} 通用反查入口
     * （其余枚举各自内聚了 fromCode）。
     * </p>
     */
    private PaymentFrequency parseFrequency(String code) {
        return code != null ? BaseEnum.fromCode(PaymentFrequency.class, code) : null;
    }

    private SubjectType parseSubjectType(String code) {
        return code != null ? SubjectType.fromCode(code) : SubjectType.PERSON;
    }

    private IdCardType parseCertType(String code) {
        return code != null ? IdCardType.fromCode(code) : null;
    }

    private CustomerGender parseGender(String code) {
        return code != null ? CustomerGender.fromCode(code) : null;
    }

    private BeneficiaryType parseBeneficiaryType(String code) {
        return code != null ? BeneficiaryType.fromCode(code) : null;
    }

    private FamilyRelation parseFamilyRelation(String code) {
        return code != null ? FamilyRelation.fromCode(code) : null;
    }
}
