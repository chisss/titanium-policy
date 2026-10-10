--liquibase formatted sql
--changeset weisun:policy-loan-repayment-202609281400
-- g04-04：贷款还款落地的读模型补列——未偿本金与已计未付利息（聚合按单利 365 分段计息，投影以还款后快照整值覆盖）。
-- 存量行（g04-03 真机验收写入的放款行，此前只有 loan_amount/repaid 两列）按下述口径回填，使读模型在首次还款前
-- 即可展示真实欠款：未结清行本金 = 放款本金，利息按「放款日 → 当下」单利 365 补算；结清行本金归零。
-- 🔴 回填仅为读模型展示值，权威仍在事件溯源聚合（读模型缺失或滞后不影响还款分配计算）。
ALTER TABLE t_policy_loan
    ADD COLUMN outstanding_principal DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '未偿本金(结清后为0)' AFTER repaid,
    ADD COLUMN unpaid_interest       DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '已计未付利息(单利365分段累计)' AFTER outstanding_principal;

UPDATE t_policy_loan
SET outstanding_principal = CASE WHEN repaid = 1 THEN 0.00 ELSE loan_amount END,
    unpaid_interest       = CASE
                                WHEN repaid = 1 OR interest_rate IS NULL OR loan_date IS NULL THEN 0.00
                                ELSE ROUND(loan_amount * interest_rate
                                    * GREATEST(DATEDIFF(CURDATE(), DATE(loan_date)), 0) / 365, 2)
                            END;

--rollback ALTER TABLE t_policy_loan DROP COLUMN unpaid_interest, DROP COLUMN outstanding_principal;
