--liquibase formatted sql
--changeset weisun:policy-loan-view-version-202609281200
-- g04-03：贷款账户升格为事件溯源聚合（PolicyLoan），t_policy_loan 自写侧状态存储表转为读模型投影表，
-- PolicyLoanView 继承 BaseView（含 @Version 乐观锁列，防止并发投影覆盖），本表补该列。
ALTER TABLE t_policy_loan
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本(读模型投影并发防覆盖)' AFTER is_deleted;
--rollback ALTER TABLE t_policy_loan DROP COLUMN version;
