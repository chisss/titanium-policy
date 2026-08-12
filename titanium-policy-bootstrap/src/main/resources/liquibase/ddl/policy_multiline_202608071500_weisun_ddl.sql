--liquibase formatted sql
-- 一单多险骨架读模型：L2 险种段 / L3 标的 / L4 责任 三张新表 + t_policy_view 扩列
-- 对应设计文档《API出单业务闭环-需求与设计》第四章（五层分层）与 7.2 节
-- View 继承 BaseView：公共列 tenant_id/create_time/update_time/version（读侧不含 created_by/updated_by/is_deleted）

-- t_policy_view 扩列：三级溯源指针 + 主险冗余 + 段数 + 等待期/犹豫期 + 收费 + 渠道
--changeset weisun:policy-15
ALTER TABLE t_policy_view ADD COLUMN proposal_id                VARCHAR(36)   COMMENT '关联意向单ID(三步出单来源,支撑三级贯通)' AFTER insurance_id;
ALTER TABLE t_policy_view ADD COLUMN underwriting_id            VARCHAR(36)   COMMENT '关联核保单ID(承保依据溯源)' AFTER proposal_id;
ALTER TABLE t_policy_view ADD COLUMN market_package_id          VARCHAR(36)   COMMENT '营销包ID(弱引用marketing域,渠道转化统计)' AFTER underwriting_id;
ALTER TABLE t_policy_view ADD COLUMN product_id                 VARCHAR(36)   COMMENT '主险产品ID(险种段真相在t_policy_product,此列为高频查询冗余)' AFTER product_code;
ALTER TABLE t_policy_view ADD COLUMN sum_insured                DECIMAL(18,2) COMMENT '主险保额(各段保额见t_policy_product)' AFTER product_id;
ALTER TABLE t_policy_view ADD COLUMN total_premium              DECIMAL(18,2) COMMENT '保单总保费(=Σ计入段保费,拒保段已剔除)' AFTER sum_insured;
ALTER TABLE t_policy_view ADD COLUMN line_count                 INT           COMMENT '险种段数量(单险种=1,一单多险>1)' AFTER total_premium;
ALTER TABLE t_policy_view ADD COLUMN waiting_period_end_date    DATETIME      COMMENT '等待期届满日(此前疾病类责任不赔)' AFTER end_date;
ALTER TABLE t_policy_view ADD COLUMN hesitation_period_end_date DATETIME      COMMENT '犹豫期届满日(此前可无条件退保)' AFTER waiting_period_end_date;
ALTER TABLE t_policy_view ADD COLUMN collection_mode            VARCHAR(32)   COMMENT '收费方式码(OFFLINE/ONLINE/FREE/PAY_AFTER_USE/WITHHOLD)' AFTER investment_account_value;
ALTER TABLE t_policy_view ADD COLUMN collection_status          VARCHAR(32)   COMMENT '收讫状态码(UNCOLLECTED/PARTIALLY_COLLECTED/COLLECTED/DEFERRED/OVERDUE)' AFTER collection_mode;
ALTER TABLE t_policy_view ADD COLUMN collected_amount           DECIMAL(18,2) COMMENT '已收保费金额' AFTER collection_status;
ALTER TABLE t_policy_view ADD COLUMN channel_id                 VARCHAR(36)   COMMENT '渠道ID(指向channel域)' AFTER collected_amount;
ALTER TABLE t_policy_view ADD COLUMN sales_channel              VARCHAR(32)   COMMENT '销售渠道大类码(AGENT/BANCASSURANCE/ONLINE/BROKER等)' AFTER channel_id;
ALTER TABLE t_policy_view ADD COLUMN agent_id                   VARCHAR(36)   COMMENT '代理人/业务员ID' AFTER sales_channel;
CREATE INDEX idx_policy_view_proposal ON t_policy_view (proposal_id);
CREATE INDEX idx_policy_view_product ON t_policy_view (product_id, tenant_id);
CREATE INDEX idx_policy_view_channel ON t_policy_view (channel_id, tenant_id);
--rollback ALTER TABLE t_policy_view DROP COLUMN proposal_id, DROP COLUMN underwriting_id, DROP COLUMN market_package_id, DROP COLUMN product_id, DROP COLUMN sum_insured, DROP COLUMN total_premium, DROP COLUMN line_count, DROP COLUMN waiting_period_end_date, DROP COLUMN hesitation_period_end_date, DROP COLUMN collection_mode, DROP COLUMN collection_status, DROP COLUMN collected_amount, DROP COLUMN channel_id, DROP COLUMN sales_channel, DROP COLUMN agent_id;

-- L2 险种段读模型表：一张保单 1..N 段，每段独立保额/保费/期间/缴费/核保结论/段状态
--changeset weisun:policy-16
CREATE TABLE IF NOT EXISTS t_policy_product (
    id                       VARCHAR(64)   NOT NULL COMMENT '主键(policyId+段序号派生,保证投影幂等)',
    policy_id                VARCHAR(36)   NOT NULL COMMENT '保单ID',
    policy_product_id        VARCHAR(64)   NOT NULL COMMENT '险种段ID(保单内唯一)',
    line_no                  INT           NOT NULL COMMENT '段序号(对应出单请求planLine序号)',
    product_category         VARCHAR(16)   NOT NULL COMMENT '产品类别(MAIN主险/RIDER附加险)',
    parent_policy_product_id VARCHAR(64)            COMMENT '依附的主险段ID(RIDER必填,MAIN为空)',
    product_id               VARCHAR(36)            COMMENT '产品ID(指向product域)',
    product_code             VARCHAR(64)            COMMENT '产品编码(快照)',
    product_name             VARCHAR(256)           COMMENT '产品名称(快照)',
    product_version          VARCHAR(32)            COMMENT '产品版本(快照,锁定出单时点定义)',
    insurance_type           VARCHAR(64)            COMMENT '险种三级分类',
    sum_insured              DECIMAL(18,2)          COMMENT '本险种保额(独立)',
    premium                  DECIMAL(18,2)          COMMENT '本险种保费(独立,拒保段不计入总保费)',
    currency                 VARCHAR(8)             COMMENT '币种(ISO4217)',
    period_start             DATETIME               COMMENT '本险种保障起期(可独立于保单主期间)',
    period_end               DATETIME               COMMENT '本险种保障止期(终身型为空)',
    period_type              VARCHAR(32)            COMMENT '保障期间类型(FIXED_TERM/WHOLE_LIFE/CUSTOM)',
    payment_frequency        VARCHAR(32)            COMMENT '本险种缴费频率(LUMP_SUM/ANNUAL/SEMI_ANNUAL/QUARTERLY/MONTHLY)',
    premium_payment_years    INT                    COMMENT '本险种缴费年数(缴费期≠保障期)',
    underwriting_conclusion  VARCHAR(32)            COMMENT '本险种核保结论(ACCEPT/MODIFY/REJECT/POSTPONE)',
    line_status              VARCHAR(32)            COMMENT '本险种承保状态(UNDERWRITING/ACCEPTED/EFFECTIVE/REJECTED/SURRENDERED/EXPIRED)',
    tenant_id                VARCHAR(32)   NOT NULL COMMENT '租户ID',
    create_time              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投影创建时间',
    update_time              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '投影更新时间',
    version                  BIGINT                 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_policy_product_line (policy_id, policy_product_id),
    KEY idx_policy_product_policy (policy_id, tenant_id),
    KEY idx_policy_product_product (product_id, tenant_id),
    KEY idx_policy_product_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保单险种段读模型表(L2,一单多险载体)';
--rollback DROP TABLE IF EXISTS t_policy_product;

-- L3 标的读模型表：全险种差异收敛点，attributes_json 由产品 subjectFieldsSchema 定义结构
--changeset weisun:policy-17
CREATE TABLE IF NOT EXISTS t_policy_subject (
    id                  VARCHAR(64)   NOT NULL COMMENT '主键(policyId+段ID+标的ID派生,保证投影幂等)',
    policy_id           VARCHAR(36)   NOT NULL COMMENT '保单ID',
    policy_product_id   VARCHAR(64)   NOT NULL COMMENT '所属险种段ID',
    subject_id          VARCHAR(64)   NOT NULL COMMENT '标的ID(保单内唯一)',
    subject_name        VARCHAR(256)           COMMENT '标的名称(车牌号/被保险人姓名/厂房名称)',
    subject_type        VARCHAR(32)   NOT NULL COMMENT '标的类型(PERSON/VEHICLE/PROPERTY/CARGO/VESSEL等)',
    customer_id         VARCHAR(36)            COMMENT '客户主数据ID(人身类标的引用customer域,非人身类为空)',
    subject_sum_insured DECIMAL(18,2)          COMMENT '本标的保额(多车/企财多分项时各不同)',
    risk_level          VARCHAR(32)            COMMENT '标的风险等级(核保回写)',
    attributes_json     TEXT                   COMMENT '类型化属性包(车辆VIN/初登日期/NCD、厂房结构/消防等级等,结构由产品subjectFieldsSchema定义)',
    tenant_id           VARCHAR(32)   NOT NULL COMMENT '租户ID',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投影创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '投影更新时间',
    version             BIGINT                 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    KEY idx_policy_subject_policy (policy_id, tenant_id),
    KEY idx_policy_subject_line (policy_product_id),
    KEY idx_policy_subject_customer (customer_id, tenant_id),
    KEY idx_policy_subject_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保单标的读模型表(L3,全险种差异收敛点)';
--rollback DROP TABLE IF EXISTS t_policy_subject;

-- L4 责任读模型表：attach_level 区分挂段(三者险/责任险)与挂标的(车损险/身故金)
--changeset weisun:policy-18
CREATE TABLE IF NOT EXISTS t_policy_coverage (
    id                  VARCHAR(64)   NOT NULL COMMENT '主键(policyId+段ID+责任编码派生,保证投影幂等)',
    policy_id           VARCHAR(36)   NOT NULL COMMENT '保单ID',
    policy_product_id   VARCHAR(64)   NOT NULL COMMENT '所属险种段ID',
    clause_id           VARCHAR(36)            COMMENT '来源条款ID',
    clause_version      VARCHAR(32)            COMMENT '来源条款版本(签发即冻结)',
    coverage_code       VARCHAR(64)            COMMENT '责任编码(快照)',
    coverage_name       VARCHAR(256)           COMMENT '责任名称(快照)',
    coverage_type       VARCHAR(50)            COMMENT '责任类型码(MEDICAL/DEATH/CRITICAL_ILLNESS/ACCIDENT)',
    attach_level        VARCHAR(16)   NOT NULL COMMENT '挂载层级(LINE段级-赔第三方或额度共享/SUBJECT标的级-赔标的自身)',
    attach_ref_id       VARCHAR(64)            COMMENT '挂载对象ID(LINE指向段ID,SUBJECT指向标的ID)',
    coverage_sum_insured DECIMAL(18,2)         COMMENT '责任保额(该责任赔付上限)',
    indemnity_ratio     DECIMAL(5,4)           COMMENT '赔付比例(1.0表示100%报销/给付)',
    deductible_type     VARCHAR(32)            COMMENT '免赔类型(NONE/FIXED_AMOUNT/PROPORTIONAL)',
    deductible_amount   DECIMAL(18,2)          COMMENT '免赔额(固定金额免赔时使用)',
    deductible_ratio    DECIMAL(5,4)           COMMENT '免赔比例(比例免赔时使用)',
    waiting_period_days INT                    COMMENT '责任级等待期天数(0=无;区别于保单级等待期)',
    payout_rule_summary VARCHAR(512)           COMMENT '赔付规则摘要(详规则在条款域)',
    tenant_id           VARCHAR(32)   NOT NULL COMMENT '租户ID',
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投影创建时间',
    update_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '投影更新时间',
    version             BIGINT                 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    KEY idx_policy_coverage_policy (policy_id, tenant_id),
    KEY idx_policy_coverage_line (policy_product_id),
    KEY idx_policy_coverage_attach (attach_level, attach_ref_id),
    KEY idx_policy_coverage_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保单保险责任读模型表(L4,理赔定责依据)';
--rollback DROP TABLE IF EXISTS t_policy_coverage;

-- 条款快照读模型表（L2.5）：保单适用的条款版本，条款域改版不影响存量保单
--changeset weisun:policy-19
CREATE TABLE IF NOT EXISTS t_policy_clause (
    id                VARCHAR(64)  NOT NULL COMMENT '主键(policyId+段ID+条款ID派生,保证投影幂等)',
    policy_id         VARCHAR(36)  NOT NULL COMMENT '保单ID',
    policy_product_id VARCHAR(64)  NOT NULL COMMENT '所属险种段ID',
    clause_id         VARCHAR(36)  NOT NULL COMMENT '条款ID(指向clause域)',
    clause_code       VARCHAR(64)           COMMENT '条款编码(快照)',
    clause_name       VARCHAR(256)          COMMENT '条款名称(快照)',
    clause_version    VARCHAR(32)           COMMENT '条款版本(快照,签发即冻结)',
    is_main_clause    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否主条款(0否1是,一段仅一个主条款)',
    tenant_id         VARCHAR(32)  NOT NULL COMMENT '租户ID',
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投影创建时间',
    update_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '投影更新时间',
    version           BIGINT                COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    KEY idx_policy_clause_policy (policy_id, tenant_id),
    KEY idx_policy_clause_line (policy_product_id),
    KEY idx_policy_clause_clause (clause_id),
    KEY idx_policy_clause_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保单条款快照读模型表(L2.5)';
--rollback DROP TABLE IF EXISTS t_policy_clause;

-- 收费信息读模型表：收费方式/账单/支付单/应收实收/收讫状态，支撑「收讫驱动生效」
--changeset weisun:policy-20
CREATE TABLE IF NOT EXISTS t_policy_collection (
    id                VARCHAR(64)   NOT NULL COMMENT '主键(policyId派生,一保单一行)',
    policy_id         VARCHAR(36)   NOT NULL COMMENT '保单ID',
    collection_mode   VARCHAR(32)            COMMENT '收费方式(OFFLINE/ONLINE/FREE/PAY_AFTER_USE/WITHHOLD)',
    bill_id           VARCHAR(64)            COMMENT '账单ID(billing域)',
    payment_order_id  VARCHAR(64)            COMMENT '支付单ID(payment域;线下与免支付无支付单)',
    payable_amount    DECIMAL(18,2)          COMMENT '应收金额',
    collected_amount  DECIMAL(18,2)          COMMENT '已收金额',
    currency          VARCHAR(8)             COMMENT '币种(ISO4217)',
    collection_status VARCHAR(32)            COMMENT '收讫状态(UNCOLLECTED/PARTIALLY_COLLECTED/COLLECTED/DEFERRED/OVERDUE)',
    collected_time    DATETIME               COMMENT '收讫时间(未收讫为空)',
    tenant_id         VARCHAR(32)   NOT NULL COMMENT '租户ID',
    create_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '投影创建时间',
    update_time       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '投影更新时间',
    version           BIGINT                 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    UNIQUE KEY uk_policy_collection_policy (policy_id),
    KEY idx_policy_collection_bill (bill_id),
    KEY idx_policy_collection_payment (payment_order_id),
    KEY idx_policy_collection_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保单收费信息读模型表';
--rollback DROP TABLE IF EXISTS t_policy_collection;

-- 投保单读模型扩列：险种段化后补主险冗余 + 收费方式 + 渠道 + 出单流水号
--changeset weisun:policy-21
ALTER TABLE t_insurance_view ADD COLUMN product_id            VARCHAR(36)   COMMENT '主险产品ID' AFTER insurance_type;
ALTER TABLE t_insurance_view ADD COLUMN sum_insured           DECIMAL(18,2) COMMENT '主险基本保额' AFTER product_id;
ALTER TABLE t_insurance_view ADD COLUMN payment_frequency     VARCHAR(32)   COMMENT '主险缴费频率' AFTER sum_insured;
ALTER TABLE t_insurance_view ADD COLUMN premium_payment_years INT           COMMENT '主险缴费年数' AFTER payment_frequency;
ALTER TABLE t_insurance_view ADD COLUMN collection_mode       VARCHAR(32)   COMMENT '收费方式' AFTER premium_payment_years;
ALTER TABLE t_insurance_view ADD COLUMN channel_id            VARCHAR(36)   COMMENT '渠道ID' AFTER collection_mode;
ALTER TABLE t_insurance_view ADD COLUMN biz_no                VARCHAR(64)   COMMENT '出单业务流水号(幂等与进度追溯)' AFTER channel_id;
ALTER TABLE t_insurance_view ADD COLUMN market_package_id     VARCHAR(36)   COMMENT '营销包ID(弱引用)' AFTER biz_no;
ALTER TABLE t_insurance_view ADD COLUMN line_count            INT           COMMENT '险种段数量' AFTER market_package_id;
CREATE INDEX idx_insurance_view_biz ON t_insurance_view (tenant_id, biz_no);
--rollback ALTER TABLE t_insurance_view DROP COLUMN product_id, DROP COLUMN sum_insured, DROP COLUMN payment_frequency, DROP COLUMN premium_payment_years, DROP COLUMN collection_mode, DROP COLUMN channel_id, DROP COLUMN biz_no, DROP COLUMN market_package_id, DROP COLUMN line_count;

-- 意向单读模型扩列：意向段化后补出单流水号 + 渠道 + 营销包
--changeset weisun:policy-22
ALTER TABLE t_proposal_view ADD COLUMN biz_no            VARCHAR(64) COMMENT '出单业务流水号' AFTER insurance_type;
ALTER TABLE t_proposal_view ADD COLUMN channel_id        VARCHAR(36) COMMENT '渠道ID' AFTER biz_no;
ALTER TABLE t_proposal_view ADD COLUMN market_package_id VARCHAR(36) COMMENT '营销包ID(弱引用)' AFTER channel_id;
ALTER TABLE t_proposal_view ADD COLUMN line_count        INT         COMMENT '意向险种段数量' AFTER market_package_id;
--rollback ALTER TABLE t_proposal_view DROP COLUMN biz_no, DROP COLUMN channel_id, DROP COLUMN market_package_id, DROP COLUMN line_count;
