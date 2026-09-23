--liquibase formatted sql

--changeset weisun:policy-shedlock-202609231800
--comment m20-10 / SPR-01：分布式定时任务锁表。本域 @Scheduled 任务加 @SchedulerLock 后，
--comment 锁记录落在此表；多副本部署时同一任务只会被一个节点执行。
CREATE TABLE IF NOT EXISTS shedlock (
    name       VARCHAR(64)  NOT NULL COMMENT '锁名（@SchedulerLock 的 name）',
    lock_until TIMESTAMP(3) NOT NULL COMMENT '锁持有至（DB 时间）',
    locked_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加锁时间',
    locked_by  VARCHAR(255) NOT NULL COMMENT '持锁节点标识',
    PRIMARY KEY (name)
);
