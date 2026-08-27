--liquibase formatted sql
-- Policy 业务编号持久化流水：租户、单据类型、业务日期隔离，避免实例重启和多实例重复发号

--changeset weisun:policy-number-sequence-1
CREATE TABLE IF NOT EXISTS t_policy_number_sequence (
    tenant_id      VARCHAR(64) NOT NULL COMMENT '租户ID',
    document_type  VARCHAR(16) NOT NULL COMMENT '单据类型(POLICY/INSURANCE/PROPOSAL)',
    business_date  DATE        NOT NULL COMMENT '业务日期',
    last_sequence  BIGINT      NOT NULL COMMENT '最近一次已分配的流水号',
    create_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (tenant_id, document_type, business_date),
    CONSTRAINT ck_policy_number_sequence_positive CHECK (last_sequence >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Policy 业务编号原子流水表';
--rollback DROP TABLE IF EXISTS t_policy_number_sequence;

--changeset weisun:policy-number-sequence-2
-- 迁移已有读模型编号，保证首次使用新发号器时从历史最大流水之后继续分配。
INSERT INTO t_policy_number_sequence (tenant_id, document_type, business_date, last_sequence)
SELECT tenant_id, 'POLICY', STR_TO_DATE(SUBSTRING(policy_no, 4, 8), '%Y%m%d'),
       MAX(CAST(SUBSTRING(policy_no, 12) AS UNSIGNED))
  FROM t_policy_view
 WHERE policy_no REGEXP '^POL[0-9]{15}$'
 GROUP BY tenant_id, STR_TO_DATE(SUBSTRING(policy_no, 4, 8), '%Y%m%d')
ON DUPLICATE KEY UPDATE last_sequence = GREATEST(last_sequence, VALUES(last_sequence));

INSERT INTO t_policy_number_sequence (tenant_id, document_type, business_date, last_sequence)
SELECT tenant_id, 'INSURANCE', STR_TO_DATE(SUBSTRING(insurance_no, 4, 8), '%Y%m%d'),
       MAX(CAST(SUBSTRING(insurance_no, 12) AS UNSIGNED))
  FROM t_insurance_view
 WHERE insurance_no REGEXP '^INS[0-9]{15}$'
 GROUP BY tenant_id, STR_TO_DATE(SUBSTRING(insurance_no, 4, 8), '%Y%m%d')
ON DUPLICATE KEY UPDATE last_sequence = GREATEST(last_sequence, VALUES(last_sequence));

INSERT INTO t_policy_number_sequence (tenant_id, document_type, business_date, last_sequence)
SELECT tenant_id, 'PROPOSAL', STR_TO_DATE(SUBSTRING(proposal_no, 4, 8), '%Y%m%d'),
       MAX(CAST(SUBSTRING(proposal_no, 12) AS UNSIGNED))
  FROM t_proposal_view
 WHERE proposal_no REGEXP '^PRP[0-9]{15}$'
 GROUP BY tenant_id, STR_TO_DATE(SUBSTRING(proposal_no, 4, 8), '%Y%m%d')
ON DUPLICATE KEY UPDATE last_sequence = GREATEST(last_sequence, VALUES(last_sequence));
