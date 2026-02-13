-- =====================================================
-- Policy Domain DDL 增量 - Phase 1 补建表结构
-- =====================================================

-- 保单参与方表（投保人/被保险人/受益人）
DROP TABLE IF EXISTS t_policy_party;
CREATE TABLE t_policy_party (
    party_id VARCHAR(36) NOT NULL COMMENT '参与方ID',
    policy_id VARCHAR(36) NOT NULL COMMENT '关联保单ID',
    party_role VARCHAR(20) NOT NULL COMMENT '参与方角色：HOLDER/INSURED/BENEFICIARY',
    customer_id VARCHAR(36) NOT NULL COMMENT '客户ID',
    name VARCHAR(50) NOT NULL COMMENT '姓名',
    cert_type VARCHAR(10) NOT NULL COMMENT '证件类型',
    cert_no VARCHAR(30) NOT NULL COMMENT '证件号码',
    phone VARCHAR(15) COMMENT '联系电话',
    email VARCHAR(50) COMMENT '邮箱',
    relation_to_holder VARCHAR(20) COMMENT '与投保人关系',
    benefit_ratio DOUBLE COMMENT '受益比例（受益人专属）',
    benefit_order INT COMMENT '受益顺序（受益人专属）',
    tenant_id VARCHAR(36) NOT NULL COMMENT '租户ID',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (party_id),
    INDEX idx_policy_party_policy (policy_id),
    INDEX idx_policy_party_customer (customer_id),
    INDEX idx_policy_party_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保单参与方表';

-- 保单状态变更日志表
DROP TABLE IF EXISTS t_policy_status_log;
CREATE TABLE t_policy_status_log (
    log_id VARCHAR(36) NOT NULL COMMENT '日志ID',
    policy_id VARCHAR(36) NOT NULL COMMENT '关联保单ID',
    from_status VARCHAR(20) NOT NULL COMMENT '原状态',
    to_status VARCHAR(20) NOT NULL COMMENT '新状态',
    change_reason VARCHAR(200) NOT NULL COMMENT '变更原因',
    operator_id VARCHAR(36) NOT NULL COMMENT '操作人ID',
    change_time DATETIME NOT NULL COMMENT '变更时间',
    tenant_id VARCHAR(36) NOT NULL COMMENT '租户ID',
    PRIMARY KEY (log_id),
    INDEX idx_status_log_policy (policy_id),
    INDEX idx_status_log_time (change_time),
    INDEX idx_status_log_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保单状态变更日志表';

-- 保全批改日志表
DROP TABLE IF EXISTS t_policy_endorsement_log;
CREATE TABLE t_policy_endorsement_log (
    endorsement_id VARCHAR(36) NOT NULL COMMENT '批改ID',
    policy_id VARCHAR(36) NOT NULL COMMENT '关联保单ID',
    endorsement_no VARCHAR(20) NOT NULL COMMENT '批改编号',
    endorsement_type VARCHAR(20) NOT NULL COMMENT '批改类型：CHANGE_HOLDER/CHANGE_BENEFICIARY/CHANGE_PAYMENT/ADD_INSURED/REMOVE_INSURED',
    from_version INT NOT NULL COMMENT '变更前版本号',
    to_version INT NOT NULL COMMENT '变更后版本号',
    change_detail JSON COMMENT '变更详情JSON',
    apply_time DATETIME NOT NULL COMMENT '申请时间',
    effective_time DATETIME COMMENT '生效时间',
    status VARCHAR(20) NOT NULL COMMENT '批改状态：APPLYING/APPROVED/EFFECTIVE/REJECTED',
    operator_id VARCHAR(36) NOT NULL COMMENT '操作人ID',
    tenant_id VARCHAR(36) NOT NULL COMMENT '租户ID',
    PRIMARY KEY (endorsement_id),
    UNIQUE KEY uk_endorsement_no (endorsement_no),
    INDEX idx_endorsement_policy (policy_id),
    INDEX idx_endorsement_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保全批改日志表';

-- 续保记录表
DROP TABLE IF EXISTS t_policy_renewal;
CREATE TABLE t_policy_renewal (
    renewal_id VARCHAR(36) NOT NULL COMMENT '续保ID',
    original_policy_id VARCHAR(36) NOT NULL COMMENT '原保单ID',
    new_policy_id VARCHAR(36) COMMENT '新保单ID',
    renewal_no VARCHAR(20) NOT NULL COMMENT '续保编号',
    renewal_status VARCHAR(20) NOT NULL COMMENT '续保状态：REMINDING/PENDING_PAYMENT/SUCCESS/FAILURE/REJECTED',
    remind_time DATETIME COMMENT '提醒时间',
    expiry_date DATETIME NOT NULL COMMENT '原保单到期日',
    renewal_start_date DATETIME COMMENT '续保起期',
    renewal_end_date DATETIME COMMENT '续保止期',
    premium_amount DECIMAL(18,2) COMMENT '续保保费',
    currency VARCHAR(3) DEFAULT 'CNY' COMMENT '币种',
    operator_id VARCHAR(36) COMMENT '操作人ID',
    tenant_id VARCHAR(36) NOT NULL COMMENT '租户ID',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (renewal_id),
    UNIQUE KEY uk_renewal_no (renewal_no),
    INDEX idx_renewal_original_policy (original_policy_id),
    INDEX idx_renewal_new_policy (new_policy_id),
    INDEX idx_renewal_status (renewal_status),
    INDEX idx_renewal_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='续保记录表';

-- 出单配置表
DROP TABLE IF EXISTS t_issuance_config;
CREATE TABLE t_issuance_config (
    config_id VARCHAR(36) NOT NULL COMMENT '配置ID',
    product_code VARCHAR(20) NOT NULL COMMENT '产品编码',
    issuance_mode VARCHAR(20) NOT NULL COMMENT '出单模式：ONE_STEP/TWO_STEP/THREE_STEP',
    requires_manual_underwriting TINYINT NOT NULL DEFAULT 0 COMMENT '是否需要人工核保',
    auto_activate TINYINT NOT NULL DEFAULT 0 COMMENT '是否自动激活',
    risk_assessment_steps JSON NOT NULL COMMENT '风控步骤列表',
    tenant_id VARCHAR(36) NOT NULL COMMENT '租户ID',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (config_id),
    UNIQUE KEY uk_issuance_config_product (product_code, tenant_id),
    INDEX idx_issuance_config_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出单配置表';
