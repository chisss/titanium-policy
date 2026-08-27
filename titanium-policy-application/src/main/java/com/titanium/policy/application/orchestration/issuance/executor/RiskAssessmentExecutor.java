package com.titanium.policy.application.orchestration.issuance.executor;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.titanium.policy.common.enums.RiskAssessmentStep;
import com.titanium.policy.common.enums.RuleEngineDecision;
import com.titanium.policy.port.RuleEngineServicePort;
import com.titanium.policy.service.RiskAssessmentDomainService;
import com.titanium.policy.valueobject.IssuanceRequest;
import com.titanium.policy.valueobject.RiskAssessmentDecision;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 风控校验执行器（应用层编排）
 * <p>
 * 只做<b>取数 + 调度</b>：按 {@link RiskAssessmentDomainService} 声明的数据需求，经
 * {@link RuleEngineServicePort} 取得外部裁决数据，再委托领域服务作出风控裁决。
 * 本类<b>不含任何风控业务判断</b>——「哪个步骤怎么裁、人工核保为何放行」等规则全部内聚在
 * 领域服务，避免业务逻辑散落到应用层导致贫血。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskAssessmentExecutor {

    private final RuleEngineServicePort       ruleEngineServicePort;
    private final RiskAssessmentDomainService riskAssessmentDomainService;

    /**
     * 执行风控校验步骤
     *
     * @param step 风控步骤
     * @param request 出单请求
     * @return 是否通过
     */
    public boolean execute(RiskAssessmentStep step, IssuanceRequest request) {
        log.info("执行风控校验步骤: {}", step.getName());

        // 编排：按领域服务声明的数据需求决定是否取规则引擎外部数据，取数后交领域服务裁决
        RiskAssessmentDecision decision;
        if (riskAssessmentDomainService.requiresRuleEngine(step)) {
            RuleEngineDecision ruleEngineDecision = invokeRuleEngine(step, request);
            decision = riskAssessmentDomainService.judge(step, ruleEngineDecision);
        } else {
            decision = riskAssessmentDomainService.judgeLocal(step);
        }

        if (!decision.passed()) {
            log.warn("风控校验不通过: 步骤={}, 原因={}", step.getName(), decision.reason());
        }
        return decision.passed();
    }

    /**
     * 调用规则引擎取得外部裁决数据（应用层 I/O，非业务判断）
     * <p>
     * 保留规则引擎 PASS/REJECT/REFER 三态；技术异常原样向上抛出，由入口释放可重试基线。
     * </p>
     *
     * @param step 风控步骤
     * @param request 出单请求
     * @return 规则引擎三态裁决
     */
    private RuleEngineDecision invokeRuleEngine(RiskAssessmentStep step, IssuanceRequest request) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("policyHolderId", request.holderCustomerId());
        variables.put("productId", request.mainProductId());
        variables.put("sumInsured", request.mainSumInsured() != null ? request.mainSumInsured().value() : null);
        variables.put("quotedPremium", request.quotedPremium() != null ? request.quotedPremium().value() : null);
        variables.put("insuredCount", request.insuredCount());
        variables.put("insuredAges", request.insuredAges());
        variables.put("lineCount", request.planLines() != null ? request.planLines().size() : 0);

        RuleEngineDecision decision = ruleEngineServicePort.executeRule(step.getCode(), variables,
                request.tenantId());
        log.info("规则引擎裁决完成: 步骤={}, 结果={}", step.getName(), decision);
        return decision;
    }
}
