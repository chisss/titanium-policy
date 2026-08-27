--liquibase formatted sql

--changeset weisun:annuity-payout-plan-version-202608140110
-- 年金给付计划实体继承 BaseView，补齐其 @Version 持久化列，恢复定时任务查询与投影更新。
ALTER TABLE t_annuity_payout_plan
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号' AFTER is_deleted;

--rollback ALTER TABLE t_annuity_payout_plan DROP COLUMN version;
