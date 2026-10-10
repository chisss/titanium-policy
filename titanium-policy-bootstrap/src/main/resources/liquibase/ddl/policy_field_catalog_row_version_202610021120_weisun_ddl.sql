--liquibase formatted sql

-- 保单字段目录**行表加版本列 + 改复合主键**（G07/g07-04）：使同一 fieldCode 的多版本行可共存。
--
-- 【为什么必须改主键，而不是"只加一列"】
--   现状主键仅 field_code ⇒ 一个字段在表里**只能存在一行**。而「按业务时点读当时生效的目录」
--   要求历史版本的行仍在：v2 新增了某字段、v3 又删掉它，则查 v2 的时点必须仍能看到它。
--   只加列不改主键时，发布新版必须**覆盖**旧行 ⇒ 历史时点只能取到最新内容，
--   「业务时点快照」名存实亡（这正是本任务要消除的缺口）。
--   ⇒ 复合主键取 (catalog_version, field_code)：catalog_version 前置，因为取数一律先按版本过滤
--     （前缀即可用上主键索引），field_code 在其内保证同一版本内字段唯一。
--
-- 【三步为何必须在同一个 changeset 内】
--   ① 加列（先允许 NULL）；② 用版本头表的**唯一一行**回填既有行；③ 收紧为 NOT NULL 并改主键。
--   ② 与 ③ 之间若被拆到不同 changeset，一旦 ② 未跑或未落盘，③ 会在 NULL 上直接失败 ——
--   但更糟的是**相反方向**：若先收紧再加列，ADD COLUMN NOT NULL 无默认值会直接失败。
--   三步是一个原子迁移，Liquibase 同一 changeset 内的语句按序执行，不拆。
--
-- 【回填值不写死字面量】取 `(SELECT catalog_version FROM t_policy_field_catalog_version)` ——
--   版本号的权威是本表和版本头表种子（202610021110），在此再抄一遍会造出第二个副本，
--   两份漂移时**不会有任何报错**（行表指向一个版本头表里不存在的版本 = 静默悬空）。
--   子查询在版本头表为空时返回 NULL ⇒ 随后的 MODIFY NOT NULL 当场失败（fail-closed），
--   不会把空版本号静默写进去。
--
-- 【存量库影响面】本 changeset 执行时行表内是 19 行（changelog-master 顺序锁定：
--   建表(28) → 等价迁移种子(30) → 加分析位列(32) → 赋分析位初值(34) → 版本头表(本次) →
--   版本头种子 → 本文件），全部回填到 2026.09.14.2，字段内容一个字节未改
--   ⇒ contentHash 仍为 541d557f…，消费方读结果不变。
--changeset weisun:policy-field-catalog-row-version-202610021120

ALTER TABLE t_policy_field_catalog
    ADD COLUMN catalog_version VARCHAR(32) NULL COMMENT '所属目录版本(复合主键之一;指向 t_policy_field_catalog_version.catalog_version)' AFTER field_code;

UPDATE t_policy_field_catalog
   SET catalog_version = (SELECT catalog_version FROM t_policy_field_catalog_version)
 WHERE catalog_version IS NULL;

ALTER TABLE t_policy_field_catalog
    MODIFY COLUMN catalog_version VARCHAR(32) NOT NULL COMMENT '所属目录版本(复合主键之一;指向 t_policy_field_catalog_version.catalog_version)',
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (catalog_version, field_code);

--rollback ALTER TABLE t_policy_field_catalog DROP PRIMARY KEY, ADD PRIMARY KEY (field_code);
--rollback ALTER TABLE t_policy_field_catalog DROP COLUMN catalog_version;
