--liquibase formatted sql

-- 保单字段目录版本头**首版种子**（G07/g07-04）：把表驱动化之前既有的版本 2026.09.14.2 登记为
-- 一个**已发布版本**，使行表 19 行的 catalog_version 有据可依。
--
-- 【为何是等价迁移而不是"新发布一版"】
--   2026.09.14.2 是表驱动化（g07-02d）之前就存在的版本号，19 个字段**逐字未变**
--   ⇒ 内容哈希与迁移前逐字相同（541d557f…，19 分量口径，由 g07-03a 三处登记）。
--   本行只是给这个既有版本补上「生效区间 + 发布证据」两项此前无处安放的事实，
--   **不产生新版本、不改变任何消费方的读结果**。
--
-- 【effective_from 为何取 2026-09-14 而非迁移执行日】
--   版本号 2026.09.14.2 的日期段即该内容的生效起始日；迁移执行日（本 changeset 运行日）与
--   「内容从哪天起算数」是两件事。取日期段是唯一不与版本号自相矛盾的来源
--   （写死其它日期会与版本号本身漂移）。
--   ⚠️ 因此本行会出现 published_at（执行时刻）晚于 effective_from（回溯生效）的形态 ——
--   这是**补记历史版本**的正常形态，不是数据错误。
--
-- 【effective_to 为 NULL】首版之后尚无后继版本；后继版本发布时按半开区间回填本行的 effective_to
--   （见版本头表 DDL 的区间语义说明）。
--
-- 【多租户】跨租户共享平台元数据，tenant_id 统一落 '1'（与行表种子、t_entity_registry 同值）。
--changeset weisun:policy-field-catalog-version-seed-202610021110

INSERT INTO t_policy_field_catalog_version
    (catalog_version, content_hash, effective_from, effective_to, published_by, published_at,
     tenant_id, create_time, update_time, version)
VALUES ('2026.09.14.2',
        '541d557fa6be3df1a3a99aa0ae3cb0c94e1c92367808dc1730ff49fc06a6cfc2',
        '2026-09-14',
        NULL,
        'system',
        NOW(),
        '1',
        NOW(),
        NOW(),
        0);

--rollback DELETE FROM t_policy_field_catalog_version WHERE catalog_version = '2026.09.14.2';
