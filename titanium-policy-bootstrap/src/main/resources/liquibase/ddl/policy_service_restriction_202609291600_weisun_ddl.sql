--liquibase formatted sql

--changeset weisun:policy-service-restriction-202609291600
-- 保单读模型补「服务受限种类」列（g04-09 挂失/补发）。
-- 背景：服务受限标记（挂失）写入 Policy 聚合后，读模型无对应列 —— 列表/详情看不见、
-- 保全建案快照取不到，属「写通读不通」（与 G7 缴费方式同形）。
-- 🔴 语义：生效中的受限种类码，多值英文逗号连接（如 LOSS_REPORT），无受限即 NULL。
-- 🔴 与 policy_status 正交：挂失保单仍是 EFFECTIVE，本列不以状态表达。
ALTER TABLE t_policy_view
    ADD COLUMN service_restriction_types VARCHAR(255) NULL
        COMMENT '生效中的服务受限种类码，逗号连接(如LOSS_REPORT)；无受限为NULL' AFTER collection_status;

--rollback ALTER TABLE t_policy_view DROP COLUMN service_restriction_types;
