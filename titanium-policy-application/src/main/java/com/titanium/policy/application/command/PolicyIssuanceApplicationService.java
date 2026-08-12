package com.titanium.policy.application.command;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.metadata.enums.BaseEnum;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.application.orchestration.issuance.orchestrator.IssuanceOrchestrator;
import com.titanium.policy.common.enums.IssuanceStage;
import com.titanium.policy.port.ProductServicePort;
import com.titanium.policy.query.repository.IssuanceProgressViewRepository;
import com.titanium.policy.query.view.IssuanceProgressView;
import com.titanium.policy.service.IssuanceEligibilityDomainService;
import com.titanium.policy.valueobject.IssuancePlanLine;
import com.titanium.policy.valueobject.IssuanceRequest;
import com.titanium.policy.valueobject.IssuanceResult;
import com.titanium.policy.valueobject.RuleDecision;
import com.titanium.policy.valueobject.product.ProductIssueRules;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 出单应用服务（写用例入口门面）
 * <p>
 * 统一出单入口的应用层门面，web 与 api provider <b>只依赖本类</b>（不得直接依赖
 * {@code orchestration}/{@code saga}——ArchUnit {@code inboundShouldOnlyDependOnApplicationEntryPoints}
 * 固化）。本门面保持薄：只做幂等判定、取数、委托编排、记录进度，不含业务规则。
 * </p>
 * <p>
 * 职责划分：
 * </p>
 * <ul>
 *   <li><b>幂等</b>（本类）：按 {@code (tenantId, bizNo)} 查进度表，已受理则返回首次结果；</li>
 *   <li><b>要素校验</b>（委托 {@link IssuanceEligibilityDomainService} 纯领域服务）：本类只负责
 *       取产品规则（跨服务取数），规则裁决在领域服务内；</li>
 *   <li><b>流程编排</b>（委托 {@link IssuanceOrchestrator}）：出单模式路由与建单。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PolicyIssuanceApplicationService {

    private final IssuanceOrchestrator             issuanceOrchestrator;
    private final IssuanceEligibilityDomainService eligibilityDomainService;
    private final ProductServicePort               productServicePort;
    private final IssuanceProgressViewRepository   issuanceProgressViewRepository;

    /**
     * 提交出单。
     * <p>
     * 流程：幂等判定 → 取产品投保规则 → 要素校验（领域服务裁决）→ 委托编排器路由建单 →
     * 记录出单进度。要素校验不通过时同步返回拒绝，不产生任何单据。
     * </p>
     *
     * @param request 出单请求
     * @return 出单结果
     */
    @Transactional
    public IssuanceResult submitIssuance(IssuanceRequest request) {
        // ① 幂等：同一业务流水号重复提交返回首次结果
        Optional<IssuanceProgressView> existing = issuanceProgressViewRepository
                .findByBizNoAndTenantId(request.bizNo(), request.tenantId());
        if (existing.isPresent()) {
            log.info("[出单入口] 业务流水号已受理，返回首次结果: bizNo={}, 当前阶段={}", request.bizNo(),
                    existing.get().getCurrentStage());
            return toResult(existing.get());
        }

        // ② 取产品投保规则（跨服务取数，属编排职责）
        Map<String, ProductIssueRules> rulesByProduct = loadIssueRules(request);

        // ③ 要素校验：规则裁决在纯领域服务内，本层不写业务判断
        RuleDecision decision = eligibilityDomainService.validate(request, rulesByProduct);
        if (!decision.passed()) {
            // 🔴 拒绝原因以「错误码 + 参数」承载，不在此拼中文句子——对外文案由 web 层按
            // Accept-Language 经 MessageSource 渲染（红线 15）。此处 defaultMessage 仅用于日志。
            log.warn("[出单入口] 投保要素校验不通过: bizNo={}, 错误码={}, 险种段={}, 默认文案={}", request.bizNo(),
                    decision.code(), decision.lineNo(), decision.defaultMessage());
            IssuanceResult rejected = IssuanceResult.rejected(request.bizNo(), decision);
            saveProgress(request, rejected);
            return rejected;
        }

        // ④ 委托编排器路由建单（一步/两步/三步由产品配置决定）
        IssuanceResult result = issuanceOrchestrator.orchestrate(request);
        saveProgress(request, result);
        return result;
    }

    /**
     * 查询出单进度。
     *
     * @param bizNo    业务流水号
     * @param tenantId 租户ID
     * @return 出单结果；流水号未受理过返回空
     */
    @Transactional(readOnly = true)
    public Optional<IssuanceResult> getIssuanceProgress(String bizNo, String tenantId) {
        return issuanceProgressViewRepository.findByBizNoAndTenantId(bizNo, tenantId).map(this::toResult);
    }

    /**
     * 取各险种段产品的投保规则（去重后逐个远程调用）。
     * <p>
     * 一单多险时多段可能指向同一产品（罕见但合法），故先按 productId 去重再取数。
     * </p>
     */
    private Map<String, ProductIssueRules> loadIssueRules(IssuanceRequest request) {
        Map<String, ProductIssueRules> rules = new HashMap<>();
        if (request.planLines() == null) {
            return rules;
        }
        for (IssuancePlanLine line : request.planLines()) {
            if (line.productId() == null || rules.containsKey(line.productId())) {
                continue;
            }
            try {
                ProductIssueRules productRules = productServicePort.getIssueRules(line.productId(),
                        request.tenantId());
                if (productRules != null) {
                    rules.put(line.productId(), productRules);
                }
            } catch (Exception ex) {
                log.warn("[出单入口] 取产品投保规则失败，跳过该产品的段级校验: productId={}", line.productId(), ex);
            }
        }
        return rules;
    }

    /**
     * 记录出单进度（幂等依据 + 进度查询数据源）。
     * <p>
     * 🔴 本表由应用层直写而非事件投影——出单受理与拒绝发生在任何聚合事件产生之前
     * （要素校验不通过时根本不建单），无事件可投影。
     * </p>
     */
    private void saveProgress(IssuanceRequest request, IssuanceResult result) {
        IssuanceProgressView view = new IssuanceProgressView();
        LocalDateTime now = LocalDateTime.now();
        view.setId(request.tenantId() + "_" + request.bizNo());
        view.setBizNo(request.bizNo());
        view.setTenantId(request.tenantId());
        view.setCreateTime(now);
        view.setUpdateTime(now);
        view.setMarketPackageId(request.marketPackageId());
        view.setIssuanceStrategy(code(result.issuanceStrategy()));
        view.setIssuanceMode(code(result.issuanceMode()));
        view.setCurrentStage(code(result.currentStage()));
        view.setProductId(request.mainProductId());
        view.setHolderCustomerId(request.holderCustomerId());
        view.setProposalId(result.proposalId());
        view.setInsuranceId(result.insuranceId());
        view.setPolicyId(result.firstPolicyId());
        view.setUnderwritingId(result.underwritingId());
        view.setBillId(result.billId());
        view.setPaymentOrderId(result.paymentOrderId());
        view.setStandardPremium(amount(result.standardPremium()));
        view.setPayablePremium(amount(result.payablePremium()));
        view.setLineCount(request.planLines() != null ? request.planLines().size() : 0);
        view.setRejectCode(result.rejectCode());
        view.setRejectReason(result.rejectReason());
        issuanceProgressViewRepository.save(view);
    }

    /**
     * 进度读模型 → 出单结果（幂等返回与进度查询共用）。
     */
    private IssuanceResult toResult(IssuanceProgressView view) {
        IssuanceResult.IssuedPolicy policy = view.getPolicyId() != null
                ? new IssuanceResult.IssuedPolicy(view.getPolicyId(), null, null,
                        view.getLineCount() != null ? view.getLineCount() : 0, null)
                : null;
        return new IssuanceResult(view.getRejectCode() == null, view.getBizNo(),
                view.getIssuanceMode() != null
                        ? com.titanium.metadata.enums.product.ProductEnum.IssuanceMode.fromCode(view.getIssuanceMode())
                        : null,
                view.getIssuanceStrategy() != null
                        ? com.titanium.metadata.enums.policy.IssuanceStrategy.fromCode(view.getIssuanceStrategy())
                        : null,
                IssuanceStage.fromCode(view.getCurrentStage()), view.getProposalId(), null, view.getInsuranceId(),
                null, policy != null ? java.util.List.of(policy) : java.util.List.of(), view.getUnderwritingId(),
                money(view.getStandardPremium()), null, money(view.getPayablePremium()), view.getBillId(),
                view.getPaymentOrderId(), null, view.getRejectCode(), view.getRejectReason());
    }

    /**
     * 枚举取 code（空安全）。
     */
    private String code(BaseEnum value) {
        return value != null ? value.getCode() : null;
    }

    /**
     * 金额取值（空安全）。
     */
    private java.math.BigDecimal amount(Money money) {
        return money != null ? money.value() : null;
    }

    /**
     * 数值 → 金额值对象（空安全，缺省币种 CNY）。
     */
    private Money money(java.math.BigDecimal value) {
        return value != null ? Money.of(value, "CNY") : null;
    }
}
