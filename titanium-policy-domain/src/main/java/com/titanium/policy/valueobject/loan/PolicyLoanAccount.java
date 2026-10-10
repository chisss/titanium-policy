package com.titanium.policy.valueobject.loan;

import java.math.BigDecimal;

/**
 * 保单贷款账户未偿视图（域侧值对象，g04-02；g04-04 改按未偿口径）。
 * <p>
 * 承载一笔未结清贷款的<b>当前欠款</b>（未偿本金、截至估值时点的未偿利息），是保全域额度校验与
 * 退费测算的取数单元。未偿本息的计算口径权威在贷款账户聚合（单利 365 分段计息，见
 * {@code PolicyLoanEntry.outstandingInterestAt}），本值对象不参与计算，仅屏蔽持久化细节。
 * </p>
 *
 * @param outstandingPrincipal 未偿本金（放款本金扣减已偿本金；结清时为 0，该明细不出现在未偿列表）
 * @param outstandingInterest 未偿利息（已计未付 + 上次计息日至估值时点的区间增量）
 */
public record PolicyLoanAccount(BigDecimal outstandingPrincipal, BigDecimal outstandingInterest) {
}
