--liquibase formatted sql

-- 保单字段目录（G07/g07-02）：保单域对外发布的**稳定字段清单**的权威存储。
--
-- 【定位】这是**平台标准元数据表**，不是某个聚合的投影产物 ——
--   与 feature-center 的 t_entity_registry 同性质（该表建表头注释是本表的直接先例）：
--   目录内容由 Liquibase/运营维护，不随业务事件变化，故列集沿用读模型惯例
--   （BaseView：tenant_id/create_time/update_time/version），
--   而非业务表的审计七列（created_by/updated_by/is_deleted）。
--
-- 【列集来源 = PolicyFieldDescriptor 的 10 个组件，一一对应，不多不少】
--   fieldCode→field_code  objectType→object_type  valueType→value_type  labelKey→label_key
--   collection→collection  objectIdentityField→object_identity_field
--   capability{readable,proposable,clearable,executionSupported,requiresObjectId,changeTypeCode}
--     →readable/proposable/clearable/execution_supported/requires_object_id/change_type_code
--   sensitivity→sensitivity  maskingPolicy→masking_policy  deprecatedAt→deprecated_at
--   ⇒ 契约 record 可从本表**无损重建**；列与组件错位会让描述符构造器的不变量（集合⇔对象标识、
--      敏感⇒掩码非 NONE、可清空⇒可提案）在读取侧静默失效。
--
-- 【为何五域表列集**逐字相同**】Policy/Customer/Claim/Billing/Channel 五个 FieldDescriptor
--   当前同为 10 组件同形状（G07 订正：形状变更统一留到 g07-03 加第 11 组件 analysis）。
--   差异只有两处：表名、object_type 的枚举。
--
-- 【为什么**不**建这几列（纪律三：无已登记消费方不立项）】
--   · catalog_version —— 目录版本在 G07/g07-02 阶段仍是各域代码常量（policy 现为
--     PolicyFieldCatalog.STANDARD_VERSION）。版本落库 + 业务时点快照（effective_from/effective_to）
--     是 **g07-04** 的任务标题本身，届时一并 ALTER；现在建列等于预造。
--   · content_hash —— 是**算出来**的（由 version + 各字段 canonicalForm 求 SHA-256），
--     不是存储事实。落列会与计算值漂移。
--   · object_type 二级索引 —— 目录规模为几十行，且当前无按对象类型过滤的消费方（g08-02 按 fieldCode 取）。
--
-- 【种子来源 🔴：由 titanium-analytical-starter 的 YAML 在 seed-mode=init 下导入，禁手写】
--   发起方是 g08-06 交付的 starter；语义是 **INIT-ONLY** —— **表非空则不碰，此后表为权威**，
--   运营侧加字段走「改表 + 发布新版本」（g07-04），**不会**被下次启动的 YAML 覆盖回去。
--   🔴 本批次因此**不产出任何 INSERT 种子**（手写种子会重新引入 G07 §六 R-2 那个已消失的
--      「须业务确认」跨团队项，见 G07 订正二与 R-8）。
--   ⚠️ 文档级矛盾的裁示：架构方案 §6.5 曾写「starter 不提供目录种子」，与同文件 §8.1（种子改由
--      starter YAML 生成）、G07 正文 §四/§g07-02/AC-09 直接冲突。**已裁示以 §8.1 为准**
--      （2026-10-01），§6.5 与 G08 文档 §2.3/§六 待同批勘误。
--
-- 【多租户】本表是**跨租户共享的平台元数据**，不按租户隔离（有意设计，非遗漏，见 G07/AC-10）。
--   tenant_id 列保留但种子统一落平台默认租户 '1'（与 t_entity_registry 同值）。
--changeset weisun:policy-field-catalog-202610011800

CREATE TABLE IF NOT EXISTS t_policy_field_catalog (
    field_code             VARCHAR(64)  NOT NULL COMMENT '稳定字段编码(业务唯一键,目录主键)',
    object_type            VARCHAR(32)  NOT NULL COMMENT 'PolicyFieldObjectType 枚举 code',
    value_type             VARCHAR(32)  NOT NULL COMMENT '字段值类型(PolicyFieldValueType 枚举 code)',
    label_key              VARCHAR(128) NOT NULL COMMENT '国际化标签键',
    collection             TINYINT(1)   NOT NULL COMMENT '是否属于集合对象(0=否,1=是)',
    object_identity_field  VARCHAR(64)           COMMENT '集合对象稳定标识字段(非集合字段为空)',
    readable               TINYINT(1)   NOT NULL COMMENT '是否允许读取',
    proposable             TINYINT(1)   NOT NULL COMMENT '是否允许提交变更提案',
    clearable              TINYINT(1)   NOT NULL COMMENT '是否允许清空',
    execution_supported    TINYINT(1)   NOT NULL COMMENT '是否已具备真实执行映射',
    requires_object_id     TINYINT(1)   NOT NULL COMMENT '是否必须指定稳定业务对象ID',
    change_type_code       VARCHAR(64)           COMMENT '业务变更类别编码(仅可提案字段非空)',
    sensitivity            VARCHAR(32)  NOT NULL COMMENT '敏感级别(PolicyFieldSensitivityLevel 枚举 code)',
    masking_policy         VARCHAR(32)  NOT NULL COMMENT '掩码策略(PolicyFieldMaskingPolicy 枚举 code)',
    deprecated_at          DATE                  COMMENT '废弃日期(未废弃为空)',
    tenant_id              VARCHAR(32)  NOT NULL COMMENT '租户ID',
    create_time            DATETIME     NOT NULL COMMENT '创建时间',
    update_time            DATETIME     NOT NULL COMMENT '更新时间',
    version                BIGINT                COMMENT '乐观锁版本号',
    PRIMARY KEY (field_code),
    KEY idx_policy_field_catalog_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='保单字段目录(平台标准元数据;种子由 starter YAML 在 seed-mode=init 导入,表非空即不碰)';

--rollback DROP TABLE IF EXISTS t_policy_field_catalog;
