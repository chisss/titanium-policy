package com.titanium.policy.valueobject.product;

/**
 * 产品条款关联值对象（防腐）
 * <p>
 * 出单时据此向条款域取条款与责任，装配保单的条款快照（L2.5）与责任快照（L4）。
 * 条款<b>版本</b>是关键字段：保单适用绑定时点的版本，条款域后续修订不影响存量保单。
 * </p>
 *
 * @param clauseId      条款ID
 * @param clauseVersion 条款版本
 * @param mainClause    是否主条款
 */
public record ProductClauseRef(String clauseId, String clauseVersion, boolean mainClause) {
}
