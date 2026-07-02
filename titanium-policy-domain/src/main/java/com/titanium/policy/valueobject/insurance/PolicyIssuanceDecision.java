package com.titanium.policy.valueobject.insurance;

import java.time.LocalDateTime;

import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.valueobject.Money;

/**
 * 承保决策值对象
 * <p>
 * 领域服务 {@code PolicyIssuanceDomainService} 依据「投保单 + 核保结果」推导出的承保决策结果，
 * 是一段纯领域计算的产物：既回答「能否承保」，也在可承保时携带「据以创建正式保单的要素」。
 * </p>
 * <p>
 * 本值对象不可变、无基础设施依赖，应用层编排器据此决定是否下发 {@code CreatePolicyCommand}，
 * 从而将「承保业务规则」（领域）与「发命令/事务」（应用）彻底解耦。
 * </p>
 *
 * @param acceptable 是否可承保（核保通过才为 true）
 * @param rejectReason 不可承保原因（acceptable=false 时有值，否则为 null）
 * @param policyForm 保单形态（可承保时有值）
 * @param holderId 投保人ID（可承保时有值）
 * @param premium 保费（可承保时有值）
 * @param insurancePeriodStart 保障起期（可承保时有值）
 * @param insurancePeriodEnd 保障止期（可承保时有值）
 * @param underwritingCondition 承保条件，如加费（MODIFY 修改条件承保时有值）
 */
public record PolicyIssuanceDecision(boolean acceptable, String rejectReason, PolicyForm policyForm, String holderId,
                                     Money premium, LocalDateTime insurancePeriodStart,
                                     LocalDateTime insurancePeriodEnd, String underwritingCondition) {

    /**
     * 构造「拒绝承保」决策
     *
     * @param rejectReason 拒绝原因
     * @return 不可承保决策
     */
    public static PolicyIssuanceDecision reject(String rejectReason) {
        return new PolicyIssuanceDecision(false, rejectReason, null, null, null, null, null, null);
    }

    /**
     * 构造「同意承保」决策
     *
     * @param policyForm 保单形态
     * @param holderId 投保人ID
     * @param premium 保费
     * @param insurancePeriodStart 保障起期
     * @param insurancePeriodEnd 保障止期
     * @param underwritingCondition 承保条件（无则传 null）
     * @return 可承保决策
     */
    public static PolicyIssuanceDecision accept(PolicyForm policyForm, String holderId, Money premium,
                                                LocalDateTime insurancePeriodStart, LocalDateTime insurancePeriodEnd,
                                                String underwritingCondition) {
        return new PolicyIssuanceDecision(true, null, policyForm, holderId, premium, insurancePeriodStart,
                insurancePeriodEnd, underwritingCondition);
    }
}
