--liquibase formatted sql
--changeset weisun:policy-31
-- g04-02：保单贷款账户表自 maintenance 库迁入 policy 库（原表从未写入、行数恒 0，原样同构迁移）。
-- 迁移理由：贷款账户是保单合同的质押负债，数据源须在保单域，保全域经取数契约读未偿本息。
-- 模型演进（未偿本金/未偿利息/还款流水）由 g04-03 状态存储聚合落地时经 Liquibase 追加，本表保持原形态。
CREATE TABLE IF NOT EXISTS t_policy_loan (
    id            VARCHAR(32)   NOT NULL COMMENT '主键(雪花)',
    policy_id     VARCHAR(36)   NOT NULL COMMENT '保单ID',
    loan_amount   DECIMAL(18,2) NOT NULL COMMENT '贷款金额',
    interest_rate DECIMAL(9,6)           COMMENT '贷款利率',
    loan_date     DATETIME               COMMENT '贷款日期',
    repaid        TINYINT       NOT NULL DEFAULT 0 COMMENT '已偿还(0否1是)',
    tenant_id     VARCHAR(32)   NOT NULL COMMENT '租户ID',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by    VARCHAR(32)   NOT NULL DEFAULT 'system' COMMENT '创建人',
    updated_by    VARCHAR(32)   NOT NULL DEFAULT 'system' COMMENT '更新人',
    is_deleted    TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除(0否1是)',
    PRIMARY KEY (id),
    KEY idx_policy_loan_tenant (tenant_id),
    KEY idx_policy_loan_policy (policy_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保单贷款账户表(g04-02 自 maintenance 库迁入,写侧状态存储)';
--rollback DROP TABLE IF EXISTS t_policy_loan;
