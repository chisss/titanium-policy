package com.titanium.policy.valueobject;

import com.titanium.policy.common.enums.RiskAssessmentStep;

/**
 * 风控步骤裁决结果值对象
 * <p>
 * 由风控领域服务（{@code RiskAssessmentDomainService}）依据「风控步骤 + 已取得的外部裁决数据」
 * 计算得出的纯领域裁决，表达某个风控步骤是否通过及其原因。不可变值对象。
 * </p>
 *
 * @param step 风控步骤
 * @param passed 是否通过
 * @param reason 裁决原因（通过时可为通过说明，不通过时为拦截原因）
 */
public record RiskAssessmentDecision(RiskAssessmentStep step, boolean passed, String reason) {

    /**
     * 构造「通过」裁决
     *
     * @param step 风控步骤
     * @return 通过裁决
     */
    public static RiskAssessmentDecision pass(RiskAssessmentStep step) {
        return new RiskAssessmentDecision(step, true, step.getName() + "通过");
    }

    /**
     * 构造「不通过」裁决
     *
     * @param step 风控步骤
     * @param reason 拦截原因
     * @return 不通过裁决
     */
    public static RiskAssessmentDecision reject(RiskAssessmentStep step, String reason) {
        return new RiskAssessmentDecision(step, false, reason);
    }
}
