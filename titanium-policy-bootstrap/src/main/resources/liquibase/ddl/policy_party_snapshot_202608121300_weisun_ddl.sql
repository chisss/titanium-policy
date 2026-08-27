--liquibase formatted sql

--changeset weisun:policy-party-snapshot-202608121300
-- 出单参与方投影补齐证件、年龄、性别和联系方式，避免后台与理赔侧只能看到客户ID/姓名。
ALTER TABLE t_policy_view
    ADD COLUMN policy_holder_id_type VARCHAR(32) COMMENT '投保人证件类型快照' AFTER policy_holder_name,
    ADD COLUMN policy_holder_id_no VARCHAR(64) COMMENT '投保人证件号码快照' AFTER policy_holder_id_type,
    ADD COLUMN policy_holder_phone VARCHAR(32) COMMENT '投保人手机号快照' AFTER policy_holder_id_no;

ALTER TABLE t_policy_insured
    ADD COLUMN id_type VARCHAR(32) COMMENT '被保险人证件类型快照' AFTER insured_name,
    ADD COLUMN id_no VARCHAR(64) COMMENT '被保险人证件号码快照' AFTER id_type,
    ADD COLUMN age INT COMMENT '被保险人年龄快照' AFTER id_no,
    ADD COLUMN gender VARCHAR(16) COMMENT '被保险人性别快照' AFTER age;

ALTER TABLE t_policy_beneficiary
    ADD COLUMN id_type VARCHAR(32) COMMENT '受益人证件类型快照' AFTER beneficiary_name,
    ADD COLUMN id_no VARCHAR(64) COMMENT '受益人证件号码快照' AFTER id_type,
    ADD COLUMN gender VARCHAR(16) COMMENT '受益人性别快照' AFTER id_no,
    ADD COLUMN phone VARCHAR(32) COMMENT '受益人手机号快照' AFTER gender;

--rollback ALTER TABLE t_policy_view DROP COLUMN policy_holder_id_type, DROP COLUMN policy_holder_id_no, DROP COLUMN policy_holder_phone;
--rollback ALTER TABLE t_policy_insured DROP COLUMN id_type, DROP COLUMN id_no, DROP COLUMN age, DROP COLUMN gender;
--rollback ALTER TABLE t_policy_beneficiary DROP COLUMN id_type, DROP COLUMN id_no, DROP COLUMN gender, DROP COLUMN phone;
