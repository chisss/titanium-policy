package com.titanium.policy.exception;

import com.titanium.metadata.exception.BusinessRuleViolationException;

/**
 * 投资账户业务规则异常
 * <p>
 * 投连险/万能险投资账户操作违反业务规则时抛出，如：
 * <ul>
 *   <li>非活跃账户禁止申购/赎回</li>
 *   <li>赎回单位数超过持有单位数</li>
 *   <li>单位净值为非正数</li>
 * </ul>
 * </p>
 *
 * @author wei.sun
 * @since 2026/6/23
 */
public class InvestmentAccountRuleException extends BusinessRuleViolationException {

    public InvestmentAccountRuleException(String ruleCode, String ruleDescription) {
        super(ruleCode, ruleDescription);
    }
}
