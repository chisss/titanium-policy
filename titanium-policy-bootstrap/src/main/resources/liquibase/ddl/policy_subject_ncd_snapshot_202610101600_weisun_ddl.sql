--liquibase formatted sql
-- G11/g11-03（AC-04）：NCD 反算取证字段随出单落库。
-- 现状：NCD 系数只是标的属性包（attributes_json）里的一个人工录入键 `ncd`，随单永久留存但
--       **无从复核它依据什么算出来**。本 changeset 把取证字段提升为独立列，使「这个系数怎么来的」
--       可被检索与复核：条数（ncd_claim_count）、区间（ncd_period_from/to）、规则版本（code/version）。
-- 关联代码：PolicyConstants.SubjectAttributeKey（属性键契约）/
--          PolicyLineProjectionEventHandler.saveSubjects（投影解析落列）/
--          PolicySubjectView + PolicySubjectQueryResult（读侧可见）。
-- 🔴 六列一律可空且**不设默认**：R-3 未裁期间产品侧规则表为空，出单链路尚未调用反算，属性包不带取证键
--    ⇒ 六列为 NULL。以 1.0 或默认区间填充会让「查不到」与「确无赔款」在落库层不可区分（AC-03 禁止）。
-- 🔴 区间止点语义为**不含**（与产品域 NcdLookbackWindow 的半开口径一致）。

--changeset weisun:policy-subject-ncd-snapshot-202610101600
ALTER TABLE t_policy_subject ADD COLUMN ncd_coefficient   DECIMAL(18,8) COMMENT 'NCD系数反算结果(NULL=未反算;绝不以1.0代填)' AFTER attributes_json;
ALTER TABLE t_policy_subject ADD COLUMN ncd_claim_count   INT           COMMENT '反算取证:统计区间内历史赔案条数(NULL=未反算;0=确无赔款)' AFTER ncd_coefficient;
ALTER TABLE t_policy_subject ADD COLUMN ncd_period_from   DATETIME      COMMENT '反算取证:统计区间起点(含)' AFTER ncd_claim_count;
ALTER TABLE t_policy_subject ADD COLUMN ncd_period_to     DATETIME      COMMENT '反算取证:统计区间止点(不含)' AFTER ncd_period_from;
ALTER TABLE t_policy_subject ADD COLUMN ncd_rule_code     VARCHAR(64)   COMMENT '反算取证:所用规则集编码' AFTER ncd_period_to;
ALTER TABLE t_policy_subject ADD COLUMN ncd_rule_version  VARCHAR(32)   COMMENT '反算取证:所用规则集版本(签发即冻结,与t_policy_clause.clause_version同法)' AFTER ncd_rule_code;
--rollback ALTER TABLE t_policy_subject DROP COLUMN ncd_coefficient, DROP COLUMN ncd_claim_count, DROP COLUMN ncd_period_from, DROP COLUMN ncd_period_to, DROP COLUMN ncd_rule_code, DROP COLUMN ncd_rule_version;
