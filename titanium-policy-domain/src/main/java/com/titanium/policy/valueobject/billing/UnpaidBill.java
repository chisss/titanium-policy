package com.titanium.policy.valueobject.billing;

import java.math.BigDecimal;

/**
 * 未结账单摘要（g04-02 取数契约的计费域防腐值对象）。
 * <p>
 * 承载计费域账单中「未缴金额」这一最小事实，供保单财务快照汇总欠费金额。账单状态语义
 * （已支付/已取消/逾期等）由计费域权威，本域不复制其状态机——欠费口径 =
 * 未结账单的未缴金额合计，由取数编排层内聚。
 * </p>
 *
 * @param billId 账单ID
 * @param unpaidAmount 未缴金额（计费域账单的未缴余额）
 */
public record UnpaidBill(String billId, BigDecimal unpaidAmount) {
}
