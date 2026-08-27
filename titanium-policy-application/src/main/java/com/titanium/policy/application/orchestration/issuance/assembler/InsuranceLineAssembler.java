package com.titanium.policy.application.orchestration.issuance.assembler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.titanium.metadata.enums.insurance.SubjectType;
import com.titanium.metadata.enums.policy.PolicyLineStatus;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.entity.insurance.InsuranceLine;
import com.titanium.policy.entity.insurance.InsuredPartyList;
import com.titanium.policy.entity.policy.InsuredSubject;
import com.titanium.policy.port.ProductServicePort;
import com.titanium.policy.valueobject.IssuancePlanLine;
import com.titanium.policy.valueobject.IssuanceRequest;
import com.titanium.policy.valueobject.policy.LineCoveragePeriod;
import com.titanium.policy.valueobject.policy.LinePaymentTerms;
import com.titanium.policy.valueobject.product.ProductBasicInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 投保险种段装配器（应用层，出单编排的取数+组装步骤）
 * <p>
 * 把出单请求的<b>投保意图</b>（{@link IssuancePlanLine}，调用方声明的期望）装配为<b>投保段</b>
 * （{@link InsuranceLine}，系统受理后的结构化载体）：补段ID、推导保障期间、组装缴费条件、
 * 装配标的、取产品快照信息。
 * </p>
 * <p>
 * <b>为何在 application 而非 domain</b>：装配需调 {@link ProductServicePort} 取产品名称与险种分类
 * （跨服务取数），依规约「调外部 Port 的编排属 application」。段的<b>业务规则</b>（构成不变量、
 * 加费计算）仍在领域侧（{@code PolicyCompositionDomainService} / {@code InsuranceLine}）。
 * </p>
 * <p>
 * 条款与责任快照<b>不在此装配</b>——投保阶段尚未锁定条款版本（核保后才锁），故 {@code InsuranceLine}
 * 无快照字段；快照在承保出单时由 {@code PolicyProductAssembler} 装配（见其类注释）。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsuranceLineAssembler {

    private final ProductServicePort productServicePort;

    /**
     * 装配投保险种段列表。
     * <p>
     * 段ID在此生成并回填 RIDER 的 parentLineId——调用方以 {@code lineNo} 表达依附关系
     * （提交时段ID尚不存在），装配时转换为真实段ID引用。
     * </p>
     *
     * @param request 出单请求
     * @return 投保段列表（顺序与方案行一致）
     */
    public List<InsuranceLine> assemble(IssuanceRequest request) {
        List<IssuancePlanLine> planLines = request.planLines();
        if (planLines == null || planLines.isEmpty()) {
            return List.of();
        }
        // 先为每个方案行分配段ID，供 RIDER 回填 parentLineId
        java.util.Map<Integer, String> lineIdByNo = new java.util.HashMap<>();
        for (IssuancePlanLine planLine : planLines) {
            lineIdByNo.put(planLine.lineNo(), UUID.randomUUID().toString());
        }

        List<InsuranceLine> lines = new ArrayList<>();
        for (IssuancePlanLine planLine : planLines) {
            lines.add(assembleLine(request, planLine, lineIdByNo));
        }
        log.info("投保险种段装配完成: bizNo={}, 段数={}", request.bizNo(), lines.size());
        return List.copyOf(lines);
    }

    /**
     * 装配单个投保段。
     */
    private InsuranceLine assembleLine(IssuanceRequest request, IssuancePlanLine planLine,
                                       java.util.Map<Integer, String> lineIdByNo) {
        String lineId = lineIdByNo.get(planLine.lineNo());
        String parentLineId = planLine.parentLineNo() != null ? lineIdByNo.get(planLine.parentLineNo()) : null;
        ProductBasicInfo product = productServicePort.getProductBasicInfo(planLine.productId(),
                request.tenantId());

        return new InsuranceLine(lineId, planLine.lineNo(), planLine.productCategory(), parentLineId,
                planLine.productId(), product != null ? product.productCode() : null,
                product != null ? product.productName() : null, product != null ? product.productVersion() : null,
                product != null ? product.insuranceType() : request.insuranceType(), planLine.sumInsured(), null,
                assemblePeriod(request, planLine), assemblePaymentTerms(planLine),
                assembleSubjects(request, planLine), null, null, PolicyLineStatus.UNDERWRITING);
    }

    /**
     * 装配段级保障期间：方案行声明期限数值时按数值推导止期，否则沿用保单主期间。
     */
    private LineCoveragePeriod assemblePeriod(IssuanceRequest request, IssuancePlanLine planLine) {
        if (planLine.coveragePeriodValue() != null && planLine.coveragePeriodUnit() != null
                && request.periodStart() != null) {
            java.time.LocalDateTime end = switch (planLine.coveragePeriodUnit()) {
                case YEAR -> request.periodStart().plusYears(planLine.coveragePeriodValue());
                case MONTH -> request.periodStart().plusMonths(planLine.coveragePeriodValue());
                case DAY -> request.periodStart().plusDays(planLine.coveragePeriodValue());
            };
            return LineCoveragePeriod.fixedTerm(request.periodStart(), end, planLine.coveragePeriodValue(),
                    planLine.coveragePeriodUnit());
        }
        return LineCoveragePeriod.fixedTerm(request.periodStart(), request.periodEnd(), null, null);
    }

    /**
     * 装配段级缴费条件（缴费频率 + 缴费年数；缺省趸缴）。
     */
    private LinePaymentTerms assemblePaymentTerms(IssuancePlanLine planLine) {
        if (planLine.paymentFrequency() == null) {
            return LinePaymentTerms.lumpSum();
        }
        return new LinePaymentTerms(planLine.paymentFrequency(),
                planLine.premiumPaymentYears() != null ? planLine.premiumPaymentYears() : 1);
    }

    /**
     * 装配段级标的：人身类以 customerId 引用客户主数据并取参与方清单中的姓名，物类以属性包承载。
     * <p>
     * 方案行未声明标的时，兜底以参与方清单的被保险人生成人身类标的——寿险/医疗险的标的即被保险人，
     * 调用方通常只传参与方而不单独声明标的。
     * </p>
     */
    private List<InsuredSubject> assembleSubjects(IssuanceRequest request, IssuancePlanLine planLine) {
        if (planLine.subjects() != null && !planLine.subjects().isEmpty()) {
            List<InsuredSubject> subjects = new ArrayList<>();
            for (IssuancePlanLine.SubjectIntent intent : planLine.subjects()) {
                subjects.add(toSubject(intent, planLine.sumInsured()));
            }
            return List.copyOf(subjects);
        }
        return subjectsFromInsuredParties(request, planLine);
    }

    /**
     * 标的意图 → 标的实体。
     */
    private InsuredSubject toSubject(IssuancePlanLine.SubjectIntent intent, Money lineSumInsured) {
        Money sumInsured = intent.subjectSumInsured() != null ? intent.subjectSumInsured() : lineSumInsured;
        String subjectId = UUID.randomUUID().toString();
        if (intent.isPerson()) {
            return InsuredSubject.ofPerson(subjectId, intent.customerId(), intent.subjectName(), sumInsured,
                    intent.attributes());
        }
        return InsuredSubject.ofObject(subjectId, intent.subjectType(), intent.subjectName(), sumInsured,
                intent.attributes());
    }

    /**
     * 从参与方清单的被保险人兜底生成人身类标的（寿险/医疗险常见场景）。
     */
    private List<InsuredSubject> subjectsFromInsuredParties(IssuanceRequest request, IssuancePlanLine planLine) {
        InsuredPartyList partyList = request.insuredPartyList();
        if (partyList == null || partyList.insuredList() == null || partyList.insuredList().isEmpty()) {
            return List.of();
        }
        List<InsuredSubject> subjects = new ArrayList<>();
        for (InsuredPartyList.InsuredInfo insured : partyList.insuredList()) {
            java.util.Map<String, Object> attributes = new java.util.HashMap<>();
            attributes.put("age", insured.age());
            if (insured.gender() != null) {
                attributes.put("gender", insured.gender().getCode());
            }
            subjects.add(new InsuredSubject(UUID.randomUUID().toString(), insured.name(), SubjectType.PERSON,
                    insured.customerId(), planLine.sumInsured(), null, attributes));
        }
        return List.copyOf(subjects);
    }
}
