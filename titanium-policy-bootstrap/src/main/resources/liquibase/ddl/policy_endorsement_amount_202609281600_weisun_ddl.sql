--liquibase formatted sql
--changeset weisun:policy-endorsement-amount-202609281600
-- g04-06：批单财务留痕补列——金额型保全（追加/减额缴费、追加投资、部分领取）无字段变更，
-- 其合同效力就是一笔收付差额，若只在 change_summary 里以文本承载，下游对账/监管报送无法按金额与方向取数。
-- 故把「金额 + 方向 + 币种 + 所属保全项」落为结构化列。
-- 🔴 全部可空：字段型/状态型批单本就没有金额事实，空值即「该批单不涉及收付」，与「金额为 0」语义不同。
-- 权威仍在事件溯源聚合（PolicyMaintenanceAppliedEvent.amountFact），读模型缺失或滞后不影响合同状态。
ALTER TABLE t_policy_endorsement_view
    ADD COLUMN amount_item_code VARCHAR(64)   COMMENT '金额型保全项编码(非金额型为空)' AFTER change_summary,
    ADD COLUMN amount_value     DECIMAL(18,2) COMMENT '收付金额(正数绝对值,非金额型为空)' AFTER amount_item_code,
    ADD COLUMN amount_direction VARCHAR(10)   COMMENT '收付方向(DEBIT/CREDIT,非金额型为空)' AFTER amount_value,
    ADD COLUMN amount_currency  VARCHAR(3)    COMMENT '币种(三字母代码,非金额型为空)' AFTER amount_direction;

--rollback ALTER TABLE t_policy_endorsement_view DROP COLUMN amount_currency, DROP COLUMN amount_direction, DROP COLUMN amount_value, DROP COLUMN amount_item_code;
