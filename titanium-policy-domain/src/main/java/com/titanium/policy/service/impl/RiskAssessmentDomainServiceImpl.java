package com.titanium.policy.service.impl;

import org.springframework.stereotype.Service;

import com.titanium.policy.common.enums.RiskAssessmentStep;
import com.titanium.policy.common.enums.RuleEngineDecision;
import com.titanium.policy.service.RiskAssessmentDomainService;
import com.titanium.policy.valueobject.RiskAssessmentDecision;

/**
 * 风控裁决领域服务实现
 * <p>
 * 纯领域计算：仅依赖入参枚举与领域规则，不注入任何 Port、不发命令、无状态。
 * 风控步骤的裁决规则集中于此，应用层 {@code RiskAssessmentExecutor} 不再散落
 * 「哪个步骤怎么判」「人工核保为何放行」的业务判断。
 * </p>
 */
@Service
public class RiskAssessmentDomainServiceImpl implements RiskAssessmentDomainService {

    @Override
    public boolean requiresRuleEngine(RiskAssessmentStep step) {
        // 规则：仅基础自动核保依赖规则引擎外部裁决；其余步骤为本地规则（含调用其它域的桩，后续按需扩展）
        return step == RiskAssessmentStep.BASIC_UNDERWRITING;
    }

    @Override
    public RiskAssessmentDecision judge(RiskAssessmentStep step, RuleEngineDecision ruleEngineDecision) {
        if (ruleEngineDecision == null) {
            throw new IllegalArgumentException("规则引擎裁决不能为空");
        }
        return switch (ruleEngineDecision) {
            case PASS -> RiskAssessmentDecision.pass(step);
            case REJECT -> RiskAssessmentDecision.reject(step, step.getName() + "未通过（规则引擎裁决拒绝）");
            case REFER -> RiskAssessmentDecision.refer(step);
        };
    }

    @Override
    public RiskAssessmentDecision judgeLocal(RiskAssessmentStep step) {
        // 规则：人工核保不在同步出单流程中裁决，放行以推进流程，实际核保由后续人工/Saga 环节处理
        if (step == RiskAssessmentStep.MANUAL_UNDERWRITING) {
            return RiskAssessmentDecision.pass(step);
        }
        // 规则：黑名单/重复投保/反洗钱/健康告知等本地风控步骤，当前默认通过（规则明确后在此内聚判定逻辑）
        return RiskAssessmentDecision.pass(step);
    }
}
