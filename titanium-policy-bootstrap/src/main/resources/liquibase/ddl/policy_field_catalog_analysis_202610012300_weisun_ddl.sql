--liquibase formatted sql

-- 保单字段目录「可分析」能力位（G07/g07-03/AC-03）：为 t_policy_field_catalog 增加 4 列。
--
-- 【为何是新 changeset 而不是改 202610011800 那个建表 changeset】
--   Liquibase 的 changeset 一旦应用即被 checksum 锁定：改原文件会让**已跑过的库**
--   校验失败、迁移整体中断。故加列一律新增 changeset（存量库与全新库走同一条路径）。
--
-- 【列集 = FieldAnalysisCapability 的 4 个组件，一一对应】
--   analyzable→analyzable  aggregatable→aggregatable  groupable→groupable  filterable→filterable
--   契约 record（`com.titanium.metadata.fieldcatalog.FieldAnalysisCapability`）可从本表无损重建。
--
-- 【为何 DEFAULT 0 不是「兜底」而是「占位」】
--   ALTER 加 NOT NULL 列时既有的 19 行必须有值，故给 DEFAULT 0（= 四项全关）作为**过渡态**；
--   紧随其后的 dml/policy_field_catalog_analysis_seed_202610012310 会按规则赋真值。
--   🔴 两道防线防止「只跑了 DDL、没跑 DML」这种半迁移被当成正常态：
--     ① 该 DML 自带 precondition，断言 19 个 field_code 在表中**逐一存在**（缺一行即 HALT）；
--     ② 对账脚本逐行断言 4 位与规则推导值一致（全 0 的行会被立刻抓出）。
--   半迁移态**不会静默通过**：全 0 ⇒ contentHash 与预期下界不符 ⇒ 判据报红。
--
-- 【多租户】同建表：跨租户共享平台元数据，tenant_id 仅保留不参与过滤（G07/AC-10）。
--changeset weisun:policy-field-catalog-analysis-202610012300

ALTER TABLE t_policy_field_catalog
    ADD COLUMN analyzable   TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否参与分析(总开关;为0时其余三位必须为0)',
    ADD COLUMN aggregatable TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否可作为聚合指标(TEXT/OBJECT/ARRAY 不得为1)',
    ADD COLUMN groupable    TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否可作为分组维度(collection=1 时必须为1)',
    ADD COLUMN filterable   TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否可作为筛选条件(groupable=1 时必须为1)';

--rollback ALTER TABLE t_policy_field_catalog DROP COLUMN filterable, DROP COLUMN groupable, DROP COLUMN aggregatable, DROP COLUMN analyzable;
