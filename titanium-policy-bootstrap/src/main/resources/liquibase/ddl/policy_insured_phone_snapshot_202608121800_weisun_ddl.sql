--liquibase formatted sql

--changeset weisun:policy-insured-phone-snapshot-202608121800
-- 被保险人手机号属于出单时点身份快照；独立 changeset 避免修改已执行的历史 checksum。
ALTER TABLE t_policy_insured
    ADD COLUMN phone VARCHAR(32) COMMENT '被保险人手机号快照' AFTER gender;

--rollback ALTER TABLE t_policy_insured DROP COLUMN phone;
