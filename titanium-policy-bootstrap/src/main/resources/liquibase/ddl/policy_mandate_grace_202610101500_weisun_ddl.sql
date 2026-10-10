--liquibase formatted sql
-- G11/g11-04（方案 B ①~⑩ + D1~D4 裁决）：交强 + 商业同单建模的读模型配套。
--   ① 段级险别（⑦）：t_policy_product 新增 insurance_mandate_type——法定强制（交强）/ 自愿投保（商业），
--      与险种（insurance_type）正交，随产品快照冻结；「交强不得单独解绑」守卫的识别依据（AC-10）。
--   ② 段级宽限期（D2）：等待期/犹豫期自保单级下沉段级——随各段 LineCoveragePeriod 按段冻结
--      （交强段无等待期、商业段/人身段各自取值，保单级单值表达不了），保单级两列随之 DROP。
-- 关联代码：PolicyProduct.insuranceMandateType / LineCoveragePeriod.withGracePeriods /
--          PolicyLineProjectionEventHandler.applyPeriod。

--changeset weisun:policy-mandate-grace-20261010
ALTER TABLE t_policy_product ADD COLUMN insurance_mandate_type     VARCHAR(32) COMMENT '险别码(COMPULSORY法定强制/VOLUNTARY自愿投保;与险种正交,随产品快照冻结)' AFTER insurance_type;
ALTER TABLE t_policy_product ADD COLUMN waiting_period_days        INT         COMMENT '本段等待期天数(0=无;G11/g11-04宽限期下沉段级)' AFTER period_type;
ALTER TABLE t_policy_product ADD COLUMN waiting_period_end_date    DATETIME    COMMENT '本段等待期届满日(本段起期+等待期天数;无等待期为空)' AFTER waiting_period_days;
ALTER TABLE t_policy_product ADD COLUMN hesitation_period_days     INT         COMMENT '本段犹豫期天数(0=无)' AFTER waiting_period_end_date;
ALTER TABLE t_policy_product ADD COLUMN hesitation_period_end_date DATETIME    COMMENT '本段犹豫期届满日(本段起期+犹豫期天数;无犹豫期为空)' AFTER hesitation_period_days;
ALTER TABLE t_policy_view DROP COLUMN waiting_period_end_date;
ALTER TABLE t_policy_view DROP COLUMN hesitation_period_end_date;
--rollback ALTER TABLE t_policy_view ADD COLUMN waiting_period_end_date DATETIME COMMENT '等待期届满日(此前疾病类责任不赔)' AFTER end_date; ALTER TABLE t_policy_view ADD COLUMN hesitation_period_end_date DATETIME COMMENT '犹豫期届满日(此前可无条件退保)' AFTER waiting_period_end_date; ALTER TABLE t_policy_product DROP COLUMN insurance_mandate_type, DROP COLUMN waiting_period_days, DROP COLUMN waiting_period_end_date, DROP COLUMN hesitation_period_days, DROP COLUMN hesitation_period_end_date;
