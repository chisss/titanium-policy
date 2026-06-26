package com.titanium.policy.exception;

import com.titanium.metadata.exception.BusinessRuleViolationException;

/**
 * 保单业务规则违反异常
 * <p>
 * 当保单领域的业务规则校验失败时抛出，如：
 * <ul>
 *   <li>首期保费未缴禁止保单生效</li>
 *   <li>团险投保单至少需要 2 个标的</li>
 *   <li>保障起期晚于止期</li>
 *   <li>受益人份额合计不等于 100%</li>
 * </ul>
 * </p>
 *
 * @author wei.sun
 * @since 2026/6/23
 */
public class PolicyBusinessRuleException extends BusinessRuleViolationException {

    public PolicyBusinessRuleException(String ruleCode, String ruleDescription) {
        super(ruleCode, ruleDescription);
    }
}
