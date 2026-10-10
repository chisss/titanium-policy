--liquibase formatted sql

-- 保单字段目录**版本头表**（G07/g07-04）：一个已发布版本的元数据（版本号/内容哈希/生效区间/发布证据）。
--
-- 【为什么必须有这张表 —— 行表加列装不下这些事实】
--   行表 t_policy_field_catalog 的每一行是「某版本里的某个字段」；而
--   content_hash / effective_from / effective_to / 发布人 / 发布时间是**整版本级**的事实，
--   它们与字段行是 1:N —— 摊到每一行上会让「同一版本 N 行必须逐字相同」成为一条
--   无人守护的不变量（改一行忘其它行 = 静默分叉）。故单独成表，一行一版本。
--
-- 【与行表的关系】行表 catalog_version 列指向本表 catalog_version（业务外键，不建物理 FK：
--   本仓平台元数据表一致不建 FK，迁移顺序由 changelog-master 的 include 顺序保证）。
--
-- 【生效区间语义：半开区间 [effective_from, effective_to)】
--   · effective_from **含**当日；effective_to **不含**当日，为 NULL 表示开放区间（无终止日）。
--   · 半开区间让相邻两版首尾相接而无重叠：2026.09 版 [09-14, 10-01) 与 2026.10 版 [10-01, ∞)
--     在 10-01 当日**只**命中后者，不存在「同一天两版都生效」的歧义。
--   · 区间重叠属数据缺陷，读取侧 fail-closed（解析器发现同日命中多版即拒绝），不静默取其一。
--
-- 【为什么 content_hash 落在这里、而不是像建表注释说的「不落列」】
--   建表（202610011800）时该注释的判断是「当时没有任何列需要它」；本表改变了这一点——
--   发布时冻结的哈希是**历史事实**（说「这一版发布时内容长这样」），必须落库。
--   它同时是**读取侧的活性守卫**：装配器把它传进 PolicyFieldCatalog 构造器，
--   若行表内容被改动而版本未升，算出的哈希与冻结值不符 ⇒ 当场拒绝（见 g07-04 实现）。
--   ⇒ 「算出来的」与「必须落库」不矛盾：前者是校验方式，后者是事实留存。
--
-- 【多租户】与行表同：跨租户共享的平台元数据，tenant_id 仅保留不参与过滤（G07/AC-10）。
--changeset weisun:policy-field-catalog-version-202610021100

CREATE TABLE IF NOT EXISTS t_policy_field_catalog_version (
    catalog_version  VARCHAR(32) NOT NULL COMMENT '目录版本(业务唯一键,主键;如 2026.09.14.2)',
    content_hash     CHAR(64)    NOT NULL COMMENT '该版本发布时冻结的内容 SHA-256(version+各字段canonicalForm)',
    effective_from   DATE        NOT NULL COMMENT '生效起始日(含当日)',
    effective_to     DATE                 COMMENT '生效截止日(不含当日;NULL=开放区间)',
    published_by     VARCHAR(32) NOT NULL COMMENT '发布人',
    published_at     DATETIME    NOT NULL COMMENT '发布时间',
    tenant_id        VARCHAR(32) NOT NULL COMMENT '租户ID(跨租户共享,不参与过滤)',
    create_time      DATETIME    NOT NULL COMMENT '创建时间',
    update_time      DATETIME    NOT NULL COMMENT '更新时间',
    version          BIGINT               COMMENT '乐观锁版本号',
    PRIMARY KEY (catalog_version),
    KEY idx_policy_field_catalog_version_effective (effective_from, effective_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保单字段目录版本头(已发布版本的哈希/生效区间/发布证据)';

--rollback DROP TABLE IF EXISTS t_policy_field_catalog_version;
