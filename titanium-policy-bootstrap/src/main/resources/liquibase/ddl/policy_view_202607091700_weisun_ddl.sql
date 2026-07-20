--liquibase formatted sql
-- policy 域读模型投影表（对齐 titanium-policy-query 下 *View 实体字段）
-- View 继承 BaseView：公共列 tenant_id/create_time/update_time/version；
-- 读模型不含 created_by/updated_by/is_deleted（写模型操作人/逻辑删除语义，读侧不适用）。
-- 子投影表(t_policy_insured/beneficiary/t_premium_plan/t_annuity_payout_plan)无对应实体，按方案清单核心字段+七件套建。

--changeset weisun:policy-1
CREATE TABLE IF NOT EXISTS t_policy_view (
    policy_id          VARCHAR(36)   NOT NULL COMMENT '保单ID(聚合根ID,读模型主键)',
    policy_no          VARCHAR(64)   NOT NULL COMMENT '保单号(高频查询)',
    insurance_id       VARCHAR(36)            COMMENT '关联投保单ID',
    policy_status      VARCHAR(32)   NOT NULL COMMENT '保单状态编码',
    premium            DECIMAL(18,2)          COMMENT '保费金额',
    currency           VARCHAR(8)             COMMENT '币种(ISO4217)',
    start_date         DATETIME               COMMENT '保险起期',
    end_date           DATETIME               COMMENT '保险止期',
    issue_time         DATETIME               COMMENT '签发时间',
    policy_holder_id   VARCHAR(36)            COMMENT '投保人ID',
    policy_holder_name VARCHAR(128)           COMMENT '投保人姓名',
    insured_name       VARCHAR(128)           COMMENT '被保险人姓名',
    product_code       VARCHAR(64)            COMMENT '险种编码',
    current_version    INT                    COMMENT '保单业务版本号',
    tenant_id          VARCHAR(32)   NOT NULL COMMENT '租户ID',
    create_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投影创建时间',
    update_time        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '投影更新时间',
    version            BIGINT                 COMMENT '乐观锁版本',
    PRIMARY KEY (policy_id),
    UNIQUE KEY uk_policy_view_no_tenant (policy_no, tenant_id),
    KEY idx_policy_view_tenant (tenant_id),
    KEY idx_policy_view_insurance (insurance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保单读模型表';

--changeset weisun:policy-2
CREATE TABLE IF NOT EXISTS t_insurance_view (
    insurance_id             VARCHAR(36)   NOT NULL COMMENT '投保单ID(聚合根ID,读模型主键)',
    insurance_no             VARCHAR(64)   NOT NULL COMMENT '投保单编号(高频查询)',
    proposal_id              VARCHAR(36)            COMMENT '关联意向单ID',
    policy_form              VARCHAR(32)            COMMENT '保单形态',
    holder_id                VARCHAR(36)            COMMENT '投保人ID',
    insured_count            INT                    COMMENT '被保险人数',
    exact_premium            DECIMAL(18,2)          COMMENT '精确保费金额',
    currency                 VARCHAR(8)             COMMENT '币种(ISO4217)',
    insurance_period_start   DATETIME               COMMENT '保险起期',
    insurance_period_end     DATETIME               COMMENT '保险止期',
    status_code              VARCHAR(32)   NOT NULL COMMENT '投保单状态编码',
    underwriting_result_code VARCHAR(32)            COMMENT '核保结论编码',
    underwriting_id          VARCHAR(36)            COMMENT '核保单号',
    issued_time              DATETIME               COMMENT '承保时间',
    tenant_id                VARCHAR(32)   NOT NULL COMMENT '租户ID',
    create_time              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投影创建时间',
    update_time              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '投影更新时间',
    version                  BIGINT                 COMMENT '乐观锁版本',
    PRIMARY KEY (insurance_id),
    UNIQUE KEY uk_insurance_view_no_tenant (insurance_no, tenant_id),
    KEY idx_insurance_view_tenant (tenant_id),
    KEY idx_insurance_view_proposal (proposal_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投保单读模型表';

--changeset weisun:policy-3
CREATE TABLE IF NOT EXISTS t_proposal_view (
    proposal_id            VARCHAR(36)   NOT NULL COMMENT '意向单ID(聚合根ID,读模型主键)',
    proposal_no            VARCHAR(64)   NOT NULL COMMENT '意向单编号(高频查询)',
    policy_form            VARCHAR(32)            COMMENT '保单形态',
    channel                VARCHAR(32)            COMMENT '销售渠道',
    customer_id            VARCHAR(36)            COMMENT '客户ID',
    intended_sum_insured   DECIMAL(18,2)          COMMENT '意向保额',
    intended_premium       DECIMAL(18,2)          COMMENT '意向保费',
    insurance_period_start DATETIME               COMMENT '保险起期',
    insurance_period_end   DATETIME               COMMENT '保险止期',
    expected_product_code  VARCHAR(64)            COMMENT '期望险种编码',
    status_code            VARCHAR(32)   NOT NULL COMMENT '意向单状态编码',
    tenant_id              VARCHAR(32)   NOT NULL COMMENT '租户ID',
    create_time            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投影创建时间',
    update_time            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '投影更新时间',
    version                BIGINT                 COMMENT '乐观锁版本',
    PRIMARY KEY (proposal_id),
    UNIQUE KEY uk_proposal_view_no_tenant (proposal_no, tenant_id),
    KEY idx_proposal_view_tenant (tenant_id),
    KEY idx_proposal_view_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='投保意向单读模型表';

--changeset weisun:policy-4
CREATE TABLE IF NOT EXISTS t_policy_endorsement_view (
    endorsement_no          VARCHAR(64)  NOT NULL COMMENT '批单号(读模型主键)',
    policy_id               VARCHAR(36)  NOT NULL COMMENT '保单ID',
    update_type             VARCHAR(40)  NOT NULL COMMENT '批改类型编码',
    category                VARCHAR(20)  NOT NULL COMMENT '批改大类编码',
    policy_version          INT          NOT NULL COMMENT '批改后保单版本号',
    effective_date          DATETIME              COMMENT '批单生效日',
    change_summary          VARCHAR(512)          COMMENT '变更摘要',
    requires_premium_recalc TINYINT      NOT NULL DEFAULT 0 COMMENT '是否触发保费重算',
    source_maintenance_id   VARCHAR(36)           COMMENT '来源保全案件ID',
    operator_id             VARCHAR(50)           COMMENT '操作人',
    endorsed_at             DATETIME     NOT NULL COMMENT '批改落地时间',
    tenant_id               VARCHAR(32)  NOT NULL COMMENT '租户ID',
    create_time             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投影创建时间',
    update_time             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '投影更新时间',
    version                 BIGINT                COMMENT '乐观锁版本',
    PRIMARY KEY (endorsement_no),
    KEY idx_endorsement_policy (policy_id, tenant_id),
    KEY idx_endorsement_view_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批单(保单批改流水)读模型表';

--changeset weisun:policy-5
CREATE TABLE IF NOT EXISTS t_policy_relation (
    child_policy_id  VARCHAR(36)  NOT NULL COMMENT '子保单ID(读模型主键)',
    parent_policy_id VARCHAR(36)  NOT NULL COMMENT '父保单ID',
    group_id         VARCHAR(36)           COMMENT '集团ID(团单专属)',
    linked_at        DATETIME     NOT NULL COMMENT '挂载时间',
    tenant_id        VARCHAR(32)  NOT NULL COMMENT '租户ID',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投影创建时间',
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '投影更新时间',
    version          BIGINT                COMMENT '乐观锁版本',
    PRIMARY KEY (child_policy_id),
    KEY idx_policy_relation_parent (parent_policy_id, tenant_id),
    KEY idx_policy_relation_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保单父子关系读模型表';

-- 以下 4 张为无对应 View 实体的子投影表，字段依《全域DDL重建方案清单》§3.5 核心字段 + 七件套
--changeset weisun:policy-6
CREATE TABLE IF NOT EXISTS t_policy_insured (
    id              VARCHAR(32)  NOT NULL COMMENT '主键(雪花)',
    policy_id       VARCHAR(36)  NOT NULL COMMENT '保单ID',
    customer_id     VARCHAR(36)  NOT NULL COMMENT '被保险人客户ID',
    insured_name    VARCHAR(128)          COMMENT '被保险人姓名',
    relation        VARCHAR(32)           COMMENT '与投保人关系(投保关系角色码)',
    family_relation VARCHAR(32)           COMMENT '家庭成员关系码(家庭险)',
    tenant_id       VARCHAR(32)  NOT NULL COMMENT '租户ID',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by      VARCHAR(32)  NOT NULL DEFAULT 'system' COMMENT '创建人',
    updated_by      VARCHAR(32)  NOT NULL DEFAULT 'system' COMMENT '更新人',
    is_deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除(0否1是)',
    PRIMARY KEY (id),
    KEY idx_policy_insured_policy (policy_id, tenant_id),
    KEY idx_policy_insured_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保单被保险人清单投影表';

--changeset weisun:policy-7
CREATE TABLE IF NOT EXISTS t_policy_beneficiary (
    id               VARCHAR(32)   NOT NULL COMMENT '主键(雪花)',
    policy_id        VARCHAR(36)   NOT NULL COMMENT '保单ID',
    customer_id      VARCHAR(36)   NOT NULL COMMENT '受益人客户ID',
    beneficiary_name VARCHAR(128)           COMMENT '受益人姓名',
    beneficiary_type VARCHAR(32)            COMMENT '受益类型(身故/生存等角色码)',
    order_no         INT           NOT NULL DEFAULT 1 COMMENT '受益顺序',
    share_ratio      DECIMAL(5,2)  NOT NULL DEFAULT 0.00 COMMENT '受益份额比例(%)',
    tenant_id        VARCHAR(32)   NOT NULL COMMENT '租户ID',
    create_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by       VARCHAR(32)   NOT NULL DEFAULT 'system' COMMENT '创建人',
    updated_by       VARCHAR(32)   NOT NULL DEFAULT 'system' COMMENT '更新人',
    is_deleted       TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除(0否1是)',
    PRIMARY KEY (id),
    KEY idx_policy_beneficiary_policy (policy_id, tenant_id),
    KEY idx_policy_beneficiary_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保单受益人清单投影表';

--changeset weisun:policy-8
CREATE TABLE IF NOT EXISTS t_premium_plan (
    id           VARCHAR(32)   NOT NULL COMMENT '主键(雪花)',
    policy_id    VARCHAR(36)   NOT NULL COMMENT '保单ID',
    frequency    VARCHAR(32)            COMMENT '缴费频率(趸交/年缴/月缴等码)',
    term         INT                    COMMENT '缴费期数',
    premium      DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '每期保费',
    currency     VARCHAR(3)             COMMENT '币种(ISO4217)',
    due_date     DATETIME               COMMENT '应缴日期',
    tenant_id    VARCHAR(32)   NOT NULL COMMENT '租户ID',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by   VARCHAR(32)   NOT NULL DEFAULT 'system' COMMENT '创建人',
    updated_by   VARCHAR(32)   NOT NULL DEFAULT 'system' COMMENT '更新人',
    is_deleted   TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除(0否1是)',
    PRIMARY KEY (id),
    KEY idx_premium_plan_policy (policy_id, tenant_id),
    KEY idx_premium_plan_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保费计划投影表';

--changeset weisun:policy-9
CREATE TABLE IF NOT EXISTS t_annuity_payout_plan (
    id            VARCHAR(32)   NOT NULL COMMENT '主键(雪花)',
    policy_id     VARCHAR(36)   NOT NULL COMMENT '保单ID',
    start_date    DATETIME               COMMENT '给付起始日',
    frequency     VARCHAR(32)            COMMENT '给付频率(年金给付频率码)',
    amount        DECIMAL(18,2) NOT NULL DEFAULT 0.00 COMMENT '每期给付金额',
    currency      VARCHAR(3)             COMMENT '币种(ISO4217)',
    payout_status VARCHAR(32)            COMMENT '给付状态码',
    tenant_id     VARCHAR(32)   NOT NULL COMMENT '租户ID',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    created_by    VARCHAR(32)   NOT NULL DEFAULT 'system' COMMENT '创建人',
    updated_by    VARCHAR(32)   NOT NULL DEFAULT 'system' COMMENT '更新人',
    is_deleted    TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除(0否1是)',
    PRIMARY KEY (id),
    KEY idx_annuity_payout_policy (policy_id, tenant_id),
    KEY idx_annuity_payout_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='年金给付计划投影表';
--rollback DROP TABLE IF EXISTS t_policy_view, t_insurance_view, t_proposal_view, t_policy_endorsement_view, t_policy_relation, t_policy_insured, t_policy_beneficiary, t_premium_plan, t_annuity_payout_plan;

-- 险种三级分类(InsuranceProductType)贯穿意向单→投保单→保单读模型，支撑按险种差异化查询/统计
--changeset weisun:policy-10
ALTER TABLE t_proposal_view  ADD COLUMN insurance_type VARCHAR(64) COMMENT '险种三级分类(TERM_LIFE/WHOLE_LIFE/ENDOWMENT/ANNUITY等)' AFTER expected_product_code;
ALTER TABLE t_insurance_view ADD COLUMN insurance_type VARCHAR(64) COMMENT '险种三级分类(TERM_LIFE/WHOLE_LIFE/ENDOWMENT/ANNUITY等)' AFTER policy_form;
ALTER TABLE t_policy_view    ADD COLUMN insurance_type VARCHAR(64) COMMENT '险种三级分类(TERM_LIFE/WHOLE_LIFE/ENDOWMENT/ANNUITY等)' AFTER end_date;
--rollback ALTER TABLE t_proposal_view DROP COLUMN insurance_type; ALTER TABLE t_insurance_view DROP COLUMN insurance_type; ALTER TABLE t_policy_view DROP COLUMN insurance_type;

-- 年金给付计划投影补齐给付进度列(期数/下一给付日)，支撑逐期推进与到期给付查询
--changeset weisun:policy-11
ALTER TABLE t_annuity_payout_plan ADD COLUMN total_installments  INT           COMMENT '总给付期数(NULL表示终身年金)' AFTER amount;
ALTER TABLE t_annuity_payout_plan ADD COLUMN paid_installments   INT           NOT NULL DEFAULT 0 COMMENT '已给付期数' AFTER total_installments;
ALTER TABLE t_annuity_payout_plan ADD COLUMN next_payout_date    DATETIME      COMMENT '下一给付日' AFTER paid_installments;
--rollback ALTER TABLE t_annuity_payout_plan DROP COLUMN total_installments; ALTER TABLE t_annuity_payout_plan DROP COLUMN paid_installments; ALTER TABLE t_annuity_payout_plan DROP COLUMN next_payout_date;

-- 保单读模型补齐寿险给付生命周期列：满期给付金/保费豁免/累计红利，支撑满期给付、豁免状态、分红查询
--changeset weisun:policy-12
ALTER TABLE t_policy_view ADD COLUMN maturity_benefit     DECIMAL(18,2) COMMENT '满期给付金额(两全/生存险满期给付后填充)' AFTER insurance_type;
ALTER TABLE t_policy_view ADD COLUMN premium_waived       TINYINT       NOT NULL DEFAULT 0 COMMENT '是否已豁免后续保费(0否1是,保单持续有效)' AFTER maturity_benefit;
ALTER TABLE t_policy_view ADD COLUMN waiver_reason        VARCHAR(32)   COMMENT '保费豁免原因码(DISABILITY/DEATH/CRITICAL_ILLNESS)' AFTER premium_waived;
ALTER TABLE t_policy_view ADD COLUMN accumulated_dividend DECIMAL(18,2) COMMENT '累计已派发红利(分红险)' AFTER waiver_reason;
ALTER TABLE t_policy_view ADD COLUMN dividend_option      VARCHAR(32)   COMMENT '红利领取方式码(CASH/ACCUMULATE/PAID_UP_ADDITION/OFFSET_PREMIUM)' AFTER accumulated_dividend;
--rollback ALTER TABLE t_policy_view DROP COLUMN maturity_benefit; ALTER TABLE t_policy_view DROP COLUMN premium_waived; ALTER TABLE t_policy_view DROP COLUMN waiver_reason; ALTER TABLE t_policy_view DROP COLUMN accumulated_dividend; ALTER TABLE t_policy_view DROP COLUMN dividend_option;

-- 保单读模型补齐投连/万能险投资账户列：账户ID与最新账户价值，支撑账户价值回写展示与净风险保额/现价计算
--changeset weisun:policy-13
ALTER TABLE t_policy_view ADD COLUMN investment_account_id    VARCHAR(64)   COMMENT '关联投资账户ID(投连/万能保单挂接)' AFTER dividend_option;
ALTER TABLE t_policy_view ADD COLUMN investment_account_value DECIMAL(18,2) COMMENT '投资账户最新价值(investment域回写,展示型最终一致)' AFTER investment_account_id;
--rollback ALTER TABLE t_policy_view DROP COLUMN investment_account_id; ALTER TABLE t_policy_view DROP COLUMN investment_account_value;

-- EN-3/4: 为被保险人/受益人投影子表补 version 列（对齐 BaseView 乐观锁契约）
--changeset weisun:policy-14
ALTER TABLE t_policy_insured    ADD COLUMN version BIGINT COMMENT '乐观锁版本号' AFTER is_deleted;
ALTER TABLE t_policy_beneficiary ADD COLUMN version BIGINT COMMENT '乐观锁版本号' AFTER is_deleted;
--rollback ALTER TABLE t_policy_insured DROP COLUMN version; ALTER TABLE t_policy_beneficiary DROP COLUMN version;
