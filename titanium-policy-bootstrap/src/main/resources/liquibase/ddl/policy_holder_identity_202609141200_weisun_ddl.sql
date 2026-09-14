--liquibase formatted sql

--changeset weisun:policy-holder-identity-202609141200
-- 保单读模型补「投保人身份要素」列（性别 / 出生日期）。
-- 背景：m15-1803 新增 policy.holder.gender / policy.holder.birthDate 字段执行器后，两项已能真实写入
-- Policy 聚合；若读模型无列则「写通读不通」——写入成功、事件已发，只是没人投影（缺口登记 G7 的复发形态）。
-- 姓名/证件类型/证件号三项的列（policy_holder_name / policy_holder_id_type / policy_holder_id_no）已存在，
-- 本次一并在投影分支贯通，无需补列。
ALTER TABLE t_policy_view
    ADD COLUMN policy_holder_gender VARCHAR(16) NULL COMMENT '投保人性别码(MALE/FEMALE/UNKNOWN)' AFTER policy_holder_id_no,
    ADD COLUMN policy_holder_birth_date DATE NULL COMMENT '投保人出生日期' AFTER policy_holder_gender;

--rollback ALTER TABLE t_policy_view DROP COLUMN policy_holder_gender, DROP COLUMN policy_holder_birth_date;
