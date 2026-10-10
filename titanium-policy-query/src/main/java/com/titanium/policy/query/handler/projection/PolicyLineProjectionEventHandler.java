package com.titanium.policy.query.handler.projection;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;

import com.titanium.common.jpa.BasePersistable;
import com.titanium.metadata.enums.BaseEnum;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.common.constant.PolicyConstants;
import com.titanium.policy.entity.policy.InsuredSubject;
import com.titanium.policy.entity.policy.PolicyProduct;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.query.repository.PolicyClauseViewRepository;
import com.titanium.policy.query.repository.PolicyCoverageViewRepository;
import com.titanium.policy.query.repository.PolicyProductViewRepository;
import com.titanium.policy.query.repository.PolicySubjectViewRepository;
import com.titanium.policy.query.view.PolicyClauseView;
import com.titanium.policy.query.view.PolicyCoverageView;
import com.titanium.policy.query.view.PolicyProductView;
import com.titanium.policy.query.view.PolicySubjectView;
import com.titanium.policy.valueobject.policy.ClauseSnapshot;
import com.titanium.policy.valueobject.policy.CoverageSnapshot;
import com.titanium.policy.valueobject.policy.LineCoveragePeriod;
import com.titanium.policy.valueobject.policy.LinePaymentTerms;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 保单险种段族读模型投影处理器（L2 段 / L2.5 条款 / L3 标的 / L4 责任）
 * <p>
 * 把 {@code PolicyCreatedEvent.policyProducts} 这一嵌套结构拆解为四张扁平读模型表，供后台一次
 * 查全保单构成、理赔域按责任定责、再保与佣金按段拆分口径。
 * </p>
 * <p>
 * <b>幂等</b>：各行主键由「policyId + 段ID(+标的ID/责任编码)」派生，事件重放时 {@code save} 覆盖同一行，
 * 不产生重复数据。段级核保结论回写事件仅更新结论与状态两列。
 * </p>
 * <p>
 * <b>边界</b>：本处理器只做「事件 → 读模型写入」，不发命令（ArchUnit
 * {@code queryShouldNotDependOnCommandGateway} 固化）。
 * </p>
 */
@Slf4j
@Component
@ProcessingGroup("policy-query-group")
@RequiredArgsConstructor
public class PolicyLineProjectionEventHandler {

    private final PolicyProductViewRepository  policyProductViewRepository;
    private final PolicyClauseViewRepository   policyClauseViewRepository;
    private final PolicySubjectViewRepository  policySubjectViewRepository;
    private final PolicyCoverageViewRepository policyCoverageViewRepository;

    /**
     * 投影保单创建事件：拆解险种段列表，逐段落地段行、条款行、标的行、责任行。
     */
    @EventHandler
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(PolicyCreatedEvent event) {
        List<PolicyProduct> lines = event.policyProducts();
        if (lines == null || lines.isEmpty()) {
            log.debug("[险种段投影] 事件无险种段，跳过: policyId={}", event.policyId());
            return;
        }
        log.info("[险种段投影] 保单创建: policyId={}, 段数={}, tenantId={}", event.policyId(), lines.size(),
                event.tenantId());
        for (PolicyProduct line : lines) {
            saveLine(event.policyId(), event.tenantId(), line);
            saveClauses(event.policyId(), event.tenantId(), line);
            saveSubjects(event.policyId(), event.tenantId(), line);
            saveCoverages(event.policyId(), event.tenantId(), line);
        }
    }

    /**
     * 落地险种段行（L2）。
     */
    private void saveLine(String policyId, String tenantId, PolicyProduct line) {
        PolicyProductView view = policyProductViewRepository.findById(lineRowId(policyId, line.policyProductId()))
                .orElseGet(PolicyProductView::new);
        view.setId(lineRowId(policyId, line.policyProductId()));
        view.setPolicyId(policyId);
        view.setPolicyProductId(line.policyProductId());
        view.setLineNo(line.lineNo());
        view.setProductCategory(code(line.productCategory()));
        view.setParentPolicyProductId(line.parentPolicyProductId());
        view.setProductId(line.productId());
        view.setProductCode(line.productCode());
        view.setProductName(line.productName());
        view.setProductVersion(line.productVersion());
        view.setPricingPlanVersion(line.pricingPlanVersion());
        view.setInsuranceType(code(line.insuranceType()));
        view.setInsuranceMandateType(code(line.insuranceMandateType()));
        view.setSumInsured(amount(line.sumInsured()));
        view.setPremium(amount(line.premium()));
        view.setCurrency(currency(line.premium() != null ? line.premium() : line.sumInsured()));
        applyPeriod(view, line.coveragePeriod());
        applyPaymentTerms(view, line.paymentTerms());
        view.setUnderwritingConclusion(code(line.underwritingConclusion()));
        view.setLineStatus(code(line.lineStatus()));
        view.setTenantId(tenantId);
        stampAuditTime(view);
        policyProductViewRepository.save(view);
    }

    /**
     * 落地段内条款快照行（L2.5）。
     */
    private void saveClauses(String policyId, String tenantId, PolicyProduct line) {
        if (line.clauseSnapshots() == null) {
            return;
        }
        for (ClauseSnapshot clause : line.clauseSnapshots()) {
            if (clause == null || clause.clauseId() == null) {
                continue;
            }
            String id = policyId + "_" + line.policyProductId() + "_" + clause.clauseId();
            PolicyClauseView view = policyClauseViewRepository.findById(id).orElseGet(PolicyClauseView::new);
            view.setId(id);
            view.setPolicyId(policyId);
            view.setPolicyProductId(line.policyProductId());
            view.setClauseId(clause.clauseId());
            view.setClauseCode(clause.clauseCode());
            view.setClauseName(clause.clauseName());
            view.setClauseVersion(clause.clauseVersion());
            view.setMainClause(clause.isMainClause());
            view.setTenantId(tenantId);
            stampAuditTime(view);
            policyClauseViewRepository.save(view);
        }
    }

    /**
     * 落地段内标的行（L3），类型化属性包序列化为 JSON 存储。
     */
    private void saveSubjects(String policyId, String tenantId, PolicyProduct line) {
        if (line.insuredSubjects() == null) {
            return;
        }
        for (InsuredSubject subject : line.insuredSubjects()) {
            if (subject == null || subject.subjectId() == null) {
                continue;
            }
            String id = policyId + "_" + line.policyProductId() + "_" + subject.subjectId();
            PolicySubjectView view = policySubjectViewRepository.findById(id).orElseGet(PolicySubjectView::new);
            view.setId(id);
            view.setPolicyId(policyId);
            view.setPolicyProductId(line.policyProductId());
            view.setSubjectId(subject.subjectId());
            view.setSubjectName(subject.subjectName());
            view.setSubjectType(code(subject.subjectType()));
            view.setCustomerId(subject.customerId());
            view.setSubjectSumInsured(amount(subject.subjectSumInsured()));
            view.setRiskLevel(code(subject.riskLevel()));
            view.setAttributesJson(subject.attributes() != null && !subject.attributes().isEmpty()
                    ? JSON.toJSONString(subject.attributes())
                    : null);
            applyNcdEvidence(view, subject);
            view.setTenantId(tenantId);
            stampAuditTime(view);
            policySubjectViewRepository.save(view);
        }
    }

    /**
     * 标的属性包中的 NCD 取证键 → 读模型六列（G11/g11-03 AC-04）。
     * <p>
     * 键契约见 {@link PolicyConstants.SubjectAttributeKey}：系数（存量键 {@code ncd}）+ 赔案条数 +
     * 区间起止 + 规则集编码/版本。属性包缺失取证键时六列**一律回写为 null**——不是「保留上次的值」：
     * 本方法在标的重投影（批改/换单）时同样会跑，若沿用加载出来的旧行，改单后会把上一版取证值留在列上，
     * 那比留空更坏（列上的数字看起来是本次反算的，实则不是）。
     * </p>
     * <p>
     * 🔴 <b>缺失即为 null，绝不以默认系数 1.0 或默认区间代填</b>——它会把「未反算 / 取不到历史」
     * 与「确无赔款」抹成同一形态，正是 AC-03 禁止的那件事。区间止点语义为**不含**（半开，
     * 与产品域 {@code NcdLookbackWindow} 一致）。
     * </p>
     */
    private void applyNcdEvidence(PolicySubjectView view, InsuredSubject subject) {
        view.setNcdCoefficient(subject.attributeAsDecimal(PolicyConstants.SubjectAttributeKey.NCD));
        view.setNcdClaimCount(subject.attributeAsInt(PolicyConstants.SubjectAttributeKey.NCD_CLAIM_COUNT));
        view.setNcdPeriodFrom(readNcdInstant(subject, PolicyConstants.SubjectAttributeKey.NCD_PERIOD_FROM));
        view.setNcdPeriodTo(readNcdInstant(subject, PolicyConstants.SubjectAttributeKey.NCD_PERIOD_TO));
        view.setNcdRuleCode(subject.attributeAsString(PolicyConstants.SubjectAttributeKey.NCD_RULE_CODE));
        view.setNcdRuleVersion(subject.attributeAsString(PolicyConstants.SubjectAttributeKey.NCD_RULE_VERSION));
    }

    /**
     * 读取 NCD 取证时间点（属性包跨序列化往返，形态不唯一，逐一容忍）。
     * <p>
     * 事件经 Axon Jackson 序列化落事件流、投影时反序列化为 {@code Map}，时间值的实际类型取决于写入侧
     * 的构造形态：可能是 {@link LocalDateTime}/{@link LocalDate}，也可能是 ISO-8601 字符串。
     * 只认一种形态会让另一形态**静默落空为 null**（列上看不出区别），故统一收敛于此。
     * </p>
     *
     * @param subject 标的
     * @param key     属性键
     * @return 时间点；缺失或不可解析返回 {@code null}（不可解析即视为未反算，不做猜测）
     */
    private LocalDateTime readNcdInstant(InsuredSubject subject, String key) {
        Object value = subject.attributes() != null ? subject.attributes().get(key) : null;
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof LocalDate date) {
            return date.atStartOfDay();
        }
        if (value instanceof String text && !text.isBlank()) {
            String trimmed = text.trim();
            try {
                return LocalDateTime.parse(trimmed);
            } catch (DateTimeParseException ignored) {
                try {
                    return LocalDate.parse(trimmed).atStartOfDay();
                } catch (DateTimeParseException notADate) {
                    log.warn("[险种段投影] NCD 取证时间不可解析，列置空: key={}, value={}", key, trimmed);
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * 落地段内保险责任行（L4），含挂载层级与挂载对象。
     */
    private void saveCoverages(String policyId, String tenantId, PolicyProduct line) {
        if (line.coverageSnapshots() == null) {
            return;
        }
        ClauseSnapshot mainClause = line.mainClause();
        for (CoverageSnapshot coverage : line.coverageSnapshots()) {
            if (coverage == null || coverage.coverageCode() == null) {
                continue;
            }
            String id = policyId + "_" + line.policyProductId() + "_" + coverage.coverageCode();
            PolicyCoverageView view = policyCoverageViewRepository.findById(id).orElseGet(PolicyCoverageView::new);
            view.setId(id);
            view.setPolicyId(policyId);
            view.setPolicyProductId(line.policyProductId());
            view.setClauseId(mainClause != null ? mainClause.clauseId() : null);
            view.setClauseVersion(mainClause != null ? mainClause.clauseVersion() : null);
            view.setCoverageCode(coverage.coverageCode());
            view.setCoverageName(coverage.coverageName());
            view.setCoverageType(coverage.coverageType());
            view.setAttachLevel(code(coverage.attachLevel()));
            view.setAttachRefId(coverage.attachRefId());
            view.setCoverageSumInsured(amount(coverage.coverageSumInsured()));
            view.setIndemnityRatio(coverage.indemnityRatio());
            view.setDeductibleType(code(coverage.deductibleType()));
            view.setDeductibleAmount(coverage.deductibleAmount());
            view.setDeductibleRatio(coverage.deductibleRatio());
            view.setWaitingPeriodDays(coverage.waitingPeriodDays());
            view.setPayoutRuleSummary(coverage.payoutRuleSummary());
            view.setTenantId(tenantId);
            stampAuditTime(view);
            policyCoverageViewRepository.save(view);
        }
    }

    /**
     * 段级保障期间 → 读模型七列（起期/止期/期间类型 + 段级宽限期四列）。
     * <p>
     * 🔴 等待期/犹豫期自 G11/g11-04（D2 裁决）由<b>段</b>承载（保单级不再持有）：交强 + 商业同单时
     * 两主险段宽限期取值本不相同，逐段落列才能按段读回（AC-11 的 D2 项按段断言）。
     * </p>
     */
    private void applyPeriod(PolicyProductView view, LineCoveragePeriod period) {
        if (period == null) {
            return;
        }
        view.setPeriodStart(period.periodStart());
        view.setPeriodEnd(period.periodEnd());
        view.setPeriodType(code(period.periodType()));
        view.setWaitingPeriodDays(period.waitingPeriodDays());
        view.setWaitingPeriodEndDate(period.waitingPeriodEndDate());
        view.setHesitationPeriodDays(period.hesitationPeriodDays());
        view.setHesitationPeriodEndDate(period.hesitationPeriodEndDate());
    }

    /**
     * 段级缴费条件 → 读模型两列（缴费频率/缴费年数）。
     */
    private void applyPaymentTerms(PolicyProductView view, LinePaymentTerms terms) {
        if (terms == null) {
            return;
        }
        view.setPaymentFrequency(code(terms.paymentFrequency()));
        view.setPremiumPaymentYears(terms.premiumPaymentYears());
    }

    /**
     * 险种段行主键（policyId + 段ID 派生，保证事件重放幂等）。
     */
    private String lineRowId(String policyId, String policyProductId) {
        return policyId + "_" + policyProductId;
    }

    /**
     * 枚举取 code（空安全）——读侧以 code 字符串存储，枚举重构不破坏存量数据。
     */
    private String code(BaseEnum value) {
        return value != null ? value.getCode() : null;
    }

    /**
     * 金额取值（空安全）。
     */
    private BigDecimal amount(Money money) {
        return money != null ? money.value() : null;
    }

    /**
     * 币种取值（空安全）。
     */
    private String currency(Money money) {
        return money != null ? money.currency() : null;
    }

    /**
     * 盖读模型审计时间戳（投影时间）。
     * <p>
     * 读模型 create_time/update_time 为投影落库时间，非空约束由投影处理器负责填充；
     * create_time 仅首次落地时写入，重放/更新只刷新 update_time。
     * </p>
     *
     * @param view 读模型（继承 {@link BasePersistable}）
     */
    private void stampAuditTime(BasePersistable view) {
        LocalDateTime now = LocalDateTime.now();
        if (view.getCreateTime() == null) {
            view.setCreateTime(now);
        }
        view.setUpdateTime(now);
    }
}
