package com.titanium.policy.command;

import java.math.BigDecimal;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * 回写投资账户价值命令（投连/万能保单账户价值变更后由投资域回写）
 * <p>
 * 投连险/万能险账户价值随单位净值调整、申购/赎回、账户转换、COI 扣费而变化，变化后由投资域经 Feign
 * 回写保单聚合，供保单展示账户价值、计算净风险保额与退保现金价值。仅投连类形态
 * （{@code PolicyForm.isInvestmentLinked()}）可回写，且须为已挂接账户。
 * </p>
 *
 * @param policyId 保单ID
 * @param accountId 投资账户ID（须与已挂接账户一致）
 * @param accountValue 最新账户价值金额（= 单位数 × 单位净值）
 * @param currency 币种
 * @param tenantId 租户ID
 */
public record UpdateAccountValueCommand(
        @TargetAggregateIdentifier String policyId,
        String accountId,
        BigDecimal accountValue,
        String currency,
        String tenantId) {
}
