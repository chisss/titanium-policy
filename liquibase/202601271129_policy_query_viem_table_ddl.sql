CREATE TABLE `t_policy_query_view` (
                                     `id`                varchar(32)                        NOT NULL COMMENT '主键ID（复用原保单表id，雪花算法）',
                                     `policy_id`         varchar(36)                        NOT NULL COMMENT '保单唯一标识',
                                     `policy_no`         varchar(64)                        NOT NULL COMMENT '保单号（高频查询字段）',
                                     `proposal_id`       varchar(32)                        NOT NULL COMMENT '投保单ID（溯源用）',
                                     `policy_type`       varchar(32)                        NOT NULL COMMENT '保单类型（INDIVIDUAL-个人/GROUP-团体/FAMILY-家庭）',
                                     `insurance_type`    varchar(32)                        NOT NULL COMMENT '险种类型（如车险/寿险/财产险等）',
                                     `clause_id`         varchar(32)                        NOT NULL COMMENT '条款ID',
                                     `policy_holder_id`  varchar(32)                        NOT NULL COMMENT '投保人ID',
                                     `insured_id`        varchar(32)                        NOT NULL COMMENT '被保人ID',
                                     `sum_insured`       decimal(18, 2)                     NOT NULL COMMENT '基础保额',
                                     `add_sum_insured`   decimal(18, 2)                     NOT NULL COMMENT '附加保额',
                                     `deductible_amount` decimal(18, 2)                     NULL COMMENT '免赔额',
                                     `premium`           decimal(18, 2)                     NOT NULL COMMENT '保费',
                                     `policy_status`     varchar(32)                        NOT NULL COMMENT '保单状态（UNDERWRITING-核保中/VALID-有效/TERMINATED-终止/EXPIRED-过期）',
                                     `start_date`        datetime                           NOT NULL COMMENT '保险起期',
                                     `end_date`          datetime                           NOT NULL COMMENT '保险止期',
                                     `issue_org`         varchar(50)                        NULL COMMENT '出单机构',
                                     `issue_time`        datetime                           NULL COMMENT '出单时间',
                                     `tenant_id`         varchar(32)                        NOT NULL COMMENT '租户ID（多租户隔离）',
                                     `create_time`       datetime DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '数据创建时间（读模型同步时间）',
                                     `update_time`       datetime DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '数据更新时间',
                                     PRIMARY KEY (`id`),
    -- 核心查询索引：保单号+租户ID（最高频查询条件）
                                     UNIQUE KEY `uk_policy_no_tenant` (`policy_no`, `tenant_id`),
    -- 辅助查询索引：投保人ID、被保人ID、保单状态、险种类型
                                     KEY `idx_policy_holder_id` (`policy_holder_id`),
                                     KEY `idx_insured_id` (`insured_id`),
                                     KEY `idx_policy_status` (`policy_status`),
                                     KEY `idx_insurance_type` (`insurance_type`),
    -- 时间范围查询索引：保险起期/止期
                                     KEY `idx_start_end_date` (`start_date`, `end_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保单读模型表（Projection）- 适配各类查询场景';