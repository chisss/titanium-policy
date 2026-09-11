package com.titanium.policy.exception;

import com.titanium.metadata.errorcode.BaseErrorCode;
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

    /**
     * 使用标准错误码枚举构造（🔴 新代码首选，见根规约 §3.4.12 红线 19）。
     *
     * @param errorCode 错误码枚举（code 取 8 位数字业务码）
     * @param message   自定义错误消息（覆盖枚举默认文案）
     */
    public PolicyBusinessRuleException(BaseErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * @deprecated 裸字符串规则码无法国际化，请改用 {@link #PolicyBusinessRuleException(BaseErrorCode, String)}
     */
    @Deprecated
    public PolicyBusinessRuleException(String ruleCode, String ruleDescription) {
        super(ruleCode, ruleDescription);
    }
}
