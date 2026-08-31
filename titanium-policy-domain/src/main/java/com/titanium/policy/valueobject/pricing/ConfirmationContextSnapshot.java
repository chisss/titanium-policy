package com.titanium.policy.valueobject.pricing;

/**
 * 确认保费计算的上下文快照（随确认请求传入 Product，用于渠道合同选择与保单年度定位）。
 * <p>
 * 取代原先的 {@code Map<String, Object>} 松散快照（规约红线 17/18）：字段强类型、
 * 结构自解释，适配器在防腐边界再转为 Product 契约的 Map 形态。
 * </p>
 *
 * @param lineId            险种段ID
 * @param lineNo            险种段序号
 * @param productCode       产品编码
 * @param issuanceReference 出单引用号
 * @param channelId         渠道ID（可空）
 * @param policyYear        保单年度（最小为 1）
 */
public record ConfirmationContextSnapshot(String lineId, Integer lineNo, String productCode,
                                          String issuanceReference, String channelId, int policyYear) {
}
