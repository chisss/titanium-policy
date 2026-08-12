-- liquibase formatted sql
-- changeset weisun:policy_issuance_progress_table

-- 出单进度读模型表（CQRS读侧）
CREATE TABLE IF NOT EXISTS t_issuance_progress (
    id VARCHAR(96) NOT NULL PRIMARY KEY COMMENT '主键（tenant_id + biz_no派生）',
    biz_no VARCHAR(64) NOT NULL COMMENT '业务流水号',
    market_package_id VARCHAR(36) COMMENT '营销包ID',
    issuance_strategy VARCHAR(32) COMMENT '出单策略（MERGE_ONE_POLICY/SPLIT_MULTI_POLICY）',
    issuance_mode VARCHAR(32) COMMENT '出单模式（ONE_STEP/TWO_STEP/THREE_STEP）',
    current_stage VARCHAR(32) NOT NULL COMMENT '当前阶段',
    product_id VARCHAR(36) COMMENT '主险产品ID',
    holder_customer_id VARCHAR(36) COMMENT '投保人客户ID',
    proposal_id VARCHAR(36) COMMENT '意向单ID',
    insurance_id VARCHAR(36) COMMENT '投保单ID',
    policy_id VARCHAR(36) COMMENT '保单ID（合并策略）',
    policy_ids VARCHAR(1024) COMMENT '保单ID列表JSON（拆分策略）',
    underwriting_id VARCHAR(36) COMMENT '核保单ID',
    bill_id VARCHAR(64) COMMENT '账单ID',
    payment_order_id VARCHAR(64) COMMENT '支付单ID',
    standard_premium DECIMAL(18,2) COMMENT '标准保费',
    payable_premium DECIMAL(18,2) COMMENT '应付保费',
    line_count INT COMMENT '险种段数量',
    reject_code VARCHAR(64) COMMENT '拒绝业务码',
    reject_reason VARCHAR(512) COMMENT '拒绝原因',
    tenant_id VARCHAR(32) NOT NULL COMMENT '租户ID',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    update_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    CONSTRAINT uk_issuance_biz UNIQUE (tenant_id, biz_no),
    INDEX idx_issuance_progress_policy (policy_id),
    INDEX idx_issuance_progress_insurance (insurance_id),
    INDEX idx_issuance_progress_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出单进度表';
