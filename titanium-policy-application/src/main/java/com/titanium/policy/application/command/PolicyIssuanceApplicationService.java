package com.titanium.policy.application.command;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.policy.IssuanceStrategy;
import com.titanium.metadata.enums.product.ProductEnum.IssuanceMode;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.application.exception.CustomerResolutionException;
import com.titanium.policy.application.exception.IssuanceOrchestrationException;
import com.titanium.policy.application.orchestration.issuance.orchestrator.IssuanceOrchestrator;
import com.titanium.policy.application.orchestration.issuance.resolver.IssuanceCustomerResolver;
import com.titanium.policy.common.enums.IssuanceStage;
import com.titanium.policy.port.ProductServicePort;
import com.titanium.policy.query.repository.InsuranceViewRepository;
import com.titanium.policy.query.repository.IssuanceProgressViewRepository;
import com.titanium.policy.query.repository.PolicyViewRepository;
import com.titanium.policy.query.repository.ProposalViewRepository;
import com.titanium.policy.query.view.InsuranceView;
import com.titanium.policy.query.view.IssuanceProgressView;
import com.titanium.policy.query.view.PolicyView;
import com.titanium.policy.query.view.ProposalView;
import com.titanium.policy.service.IssuanceEligibilityDomainService;
import com.titanium.policy.valueobject.IssuancePlanLine;
import com.titanium.policy.valueobject.IssuanceProcessConfig;
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
    private final IssuanceProgressBaselineWriter   progressBaselineWriter;
    private final IssuanceCustomerResolver          issuanceCustomerResolver;
    private final ProposalViewRepository            proposalViewRepository;
    private final InsuranceViewRepository           insuranceViewRepository;
    private final PolicyViewRepository              policyViewRepository;

    /**
     * 提交出单。
     * <p>
     * 流程：幂等判定 → 取产品投保规则 → 要素校验（领域服务裁决）→ 委托编排器路由建单 →
     * 建立初始进度。要素校验不通过时同步返回拒绝，不产生任何单据；编排开始后的阶段与单据关联
     * 由领域事件投影更新。
     * </p>
     *
     * @param request 出单请求
     * @return 出单结果
     */
    public IssuanceResult submitIssuance(IssuanceRequest request) {
        // ① 幂等：同一业务流水号重复提交返回首次结果
        Optional<IssuanceProgressView> existing = issuanceProgressViewRepository
                .findByBizNoAndTenantId(request.bizNo(), request.tenantId());
        if (existing.isPresent()) {
            log.info("[出单入口] 业务流水号已受理，返回首次结果: bizNo={}, 当前阶段={}", request.bizNo(),
                    existing.get().getCurrentStage());
            return toResult(existing.get());
        }

        // ② 先解析客户主数据，再做产品资格校验和建单；解析失败必须形成可追踪的业务拒绝。
        final IssuanceRequest resolvedRequest;
        try {
            resolvedRequest = issuanceCustomerResolver.resolve(request);
        } catch (CustomerResolutionException exception) {
            log.warn("[出单入口] 参与方客户解析失败: bizNo={}, 错误码={}", request.bizNo(), exception.errorCode());
            IssuanceResult rejected = exception.errorCodeEnum() != null
                    ? IssuanceResult.rejected(request.bizNo(), exception.errorCodeEnum(), exception.getMessage())
                    : IssuanceResult.rejected(request.bizNo(), exception.errorCode(), exception.getMessage());
            if (exception.retryable()) {
                // 客户服务瞬时故障不写幂等终态；调用方可用同一 bizNo 原样重试。
                return rejected;
            }
            return saveBaselineOrFirstResult(request, rejected).orElse(rejected);
        }

        // ③ 取产品投保规则（跨服务取数，属编排职责）；规则缺失时必须故障拒绝，禁止绕过投保限制。
        final Map<String, ProductIssueRules> rulesByProduct;
        try {
            rulesByProduct = loadIssueRules(resolvedRequest);
        } catch (RuntimeException exception) {
            log.warn("[出单入口] 产品投保规则不可用: bizNo={}", resolvedRequest.bizNo(), exception);
            IssuanceResult rejected = IssuanceResult.rejected(resolvedRequest.bizNo(),
                    RuleDecision.rejected(PolicyErrorCode.ISSUANCE_PRODUCT_RULES_UNAVAILABLE));
            return saveBaselineOrFirstResult(resolvedRequest, rejected).orElse(rejected);
        }

        // ④ 要素校验：规则裁决在纯领域服务内，本层不写业务判断
        RuleDecision decision = eligibilityDomainService.validate(resolvedRequest, rulesByProduct);
        if (!decision.passed()) {
            // 🔴 拒绝原因以「错误码 + 参数」承载，不在此拼中文句子——对外文案由 web 层按
            // Accept-Language 经 MessageSource 渲染（红线 15）。此处 defaultMessage 仅用于日志。
            log.warn("[出单入口] 投保要素校验不通过: bizNo={}, 错误码={}, 险种段={}, 默认文案={}", request.bizNo(),
                    decision.code(), decision.lineNo(), decision.defaultMessage());
            IssuanceResult rejected = IssuanceResult.rejected(resolvedRequest.bizNo(), decision);
            return saveBaselineOrFirstResult(resolvedRequest, rejected).orElse(rejected);
        }

        // ⑤ 解析一次产品出单配置，基线与后续编排共用，避免重复远程取数造成配置漂移。
        IssuanceMode issuanceMode = productServicePort.getIssuanceMode(resolvedRequest.mainProductId(),
                resolvedRequest.tenantId());
        IssuanceProcessConfig processConfig = IssuanceProcessConfig.forMode(issuanceMode,
                resolvedRequest.mainProductId());

        // ⑥ 先独立提交进度基线，再发 Axon 命令，避免 tracking processor 先于入口事务读到事件
        Optional<IssuanceResult> concurrentResult = saveBaselineOrFirstResult(resolvedRequest,
                acceptedBaseline(resolvedRequest, issuanceMode));
        if (concurrentResult.isPresent()) {
            return concurrentResult.get();
        }

        // ⑦ 委托编排器路由建单（一步/两步/三步由产品配置决定）。从发出首个命令起，进度表只由
        // 事件投影更新，入口不得与 tracking processor 并发修改同一乐观锁行。
        try {
            IssuanceResult result = issuanceOrchestrator.orchestrate(processConfig, resolvedRequest);
            if (result.currentStage() == IssuanceStage.REJECTED
                    && !progressBaselineWriter.markRejectedIfUntouched(resolvedRequest, result)) {
                log.warn("[出单入口] 同步拒绝未覆盖已推进的进度: bizNo={}", resolvedRequest.bizNo());
            }
            return result;
        } catch (IssuanceOrchestrationException exception) {
            IssuanceResult partialResult = exception.partialResult();
            if (partialResult != null) {
                log.warn("[出单入口] 编排部分完成，保留已创建单据: bizNo={}, stage={}", resolvedRequest.bizNo(),
                        partialResult.currentStage(), exception);
                return partialResult;
            }
            releaseUntouchedBaseline(resolvedRequest, exception);
            throw exception;
        } catch (RuntimeException exception) {
            releaseUntouchedBaseline(resolvedRequest, exception);
            throw exception;
        }
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
            ProductIssueRules productRules = productServicePort.getIssueRules(line.productId(), request.tenantId());
            if (productRules == null) {
                throw new IllegalStateException("产品未配置投保规则: " + line.productId());
            }
            rules.put(line.productId(), productRules);
        }
        return rules;
    }

    private IssuanceResult acceptedBaseline(IssuanceRequest request, IssuanceMode issuanceMode) {
        return new IssuanceResult(true, request.bizNo(), issuanceMode, request.issuanceStrategy(),
                IssuanceStage.ACCEPTED,
                null, null, null, null, List.of(), null, null, null, null, null, null, null, null, null);
    }

    /**
     * 保存幂等基线；并发重复提交撞唯一约束时读取并返回已提交的首次结果。
     */
    private Optional<IssuanceResult> saveBaselineOrFirstResult(IssuanceRequest request, IssuanceResult baseline) {
        try {
            progressBaselineWriter.save(request, baseline);
            return Optional.empty();
        } catch (DataIntegrityViolationException exception) {
            Optional<IssuanceProgressView> existing = issuanceProgressViewRepository
                    .findByBizNoAndTenantId(request.bizNo(), request.tenantId());
            if (existing.isPresent()) {
                log.info("[出单入口] 并发请求已建立进度基线，返回首次结果: bizNo={}, currentStage={}",
                        request.bizNo(), existing.get().getCurrentStage());
                return existing.map(this::toResult);
            }
            throw exception;
        }
    }

    private void releaseUntouchedBaseline(IssuanceRequest request, RuntimeException cause) {
        boolean released = progressBaselineWriter.releaseIfUntouched(request);
        log.warn("[出单入口] 编排技术失败: bizNo={}, 纯受理基线已释放={}", request.bizNo(), released, cause);
    }

    /**
     * 进度读模型 → 出单结果（幂等返回与进度查询共用）。
     */
    private IssuanceResult toResult(IssuanceProgressView view) {
        ProposalView proposal = findProposal(view).orElse(null);
        InsuranceView insurance = findInsurance(view).orElse(null);
        PolicyView policyView = findPolicy(view).orElse(null);
        IssuanceResult.IssuedPolicy policy = toIssuedPolicy(view, policyView);
        return new IssuanceResult(view.getRejectCode() == null, view.getBizNo(),
                view.getIssuanceMode() != null
                        ? IssuanceMode.fromCode(view.getIssuanceMode())
                        : null,
                view.getIssuanceStrategy() != null
                        ? IssuanceStrategy.fromCode(view.getIssuanceStrategy())
                        : null,
                IssuanceStage.fromCode(view.getCurrentStage()), view.getProposalId(),
                proposal != null ? proposal.getProposalNo() : null, view.getInsuranceId(),
                insurance != null ? insurance.getInsuranceNo() : null,
                policy != null ? List.of(policy) : List.of(), view.getUnderwritingId(),
                money(view.getStandardPremium()), extraPremium(view), money(view.getPayablePremium()), view.getBillId(),
                view.getPaymentOrderId(), null, view.getRejectCode(), view.getRejectReason());
    }

    private Optional<ProposalView> findProposal(IssuanceProgressView view) {
        return view.getProposalId() != null
                ? proposalViewRepository.findByProposalIdAndTenantId(view.getProposalId(), view.getTenantId())
                : Optional.empty();
    }

    private Optional<InsuranceView> findInsurance(IssuanceProgressView view) {
        return view.getInsuranceId() != null
                ? insuranceViewRepository.findByInsuranceIdAndTenantId(view.getInsuranceId(), view.getTenantId())
                : Optional.empty();
    }

    private Optional<PolicyView> findPolicy(IssuanceProgressView view) {
        return view.getPolicyId() != null
                ? policyViewRepository.findByPolicyIdAndTenantId(view.getPolicyId(), view.getTenantId())
                : Optional.empty();
    }

    private IssuanceResult.IssuedPolicy toIssuedPolicy(IssuanceProgressView progress, PolicyView policy) {
        if (progress.getPolicyId() == null) {
            return null;
        }
        int lineCount = policy != null && policy.getLineCount() != null
                ? policy.getLineCount() : progress.getLineCount() != null ? progress.getLineCount() : 0;
        String currency = policy != null && policy.getCurrency() != null ? policy.getCurrency().getCode()
                : CurrencyEnum.CNY.getCode();
        Money totalPremium = policy != null && policy.getTotalPremium() != null
                ? Money.of(policy.getTotalPremium(), currency) : null;
        return new IssuanceResult.IssuedPolicy(progress.getPolicyId(),
                policy != null ? policy.getPolicyNo() : null,
                policy != null && policy.getPolicyStatus() != null ? policy.getPolicyStatus().getCode() : null,
                lineCount, totalPremium);
    }

    /**
     * 数值 → 金额值对象（空安全，缺省币种 CNY）。
     */
    private Money money(BigDecimal value) {
        return value != null ? Money.of(value, CurrencyEnum.CNY.getCode()) : null;
    }

    private Money extraPremium(IssuanceProgressView view) {
        if (view.getStandardPremium() == null || view.getPayablePremium() == null) {
            return null;
        }
        if (view.getPayablePremium().compareTo(view.getStandardPremium()) < 0) {
            log.warn("[出单进度] 应付保费小于标准保费，跳过加费计算: bizNo={}, standard={}, payable={}",
                    view.getBizNo(), view.getStandardPremium(), view.getPayablePremium());
            return null;
        }
        return money(view.getPayablePremium().subtract(view.getStandardPremium()));
    }
}
