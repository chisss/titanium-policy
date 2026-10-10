--liquibase formatted sql

--changeset weisun:policy-underwriting-id-widen-202609271400
--comment g02-06 真机验收发现的 g02-04 回归：核保域跨域标识改为 40 字符（AUW-/MUW- + UUID），
--comment 核保域自身 4 张表均为 VARCHAR(50) 容得下，但本域 3 张读模型表仍是 VARCHAR(36)。
--comment 后果（真机实测）：投影写 t_insurance_view/t_issuance_progress 时
--comment 「Data truncation: Data too long for column 'underwriting_id'」⇒ 事件进 DLQ、
--comment retry-scheduler 反复重试，读侧永久停在 UNDERWRITING——而写侧事件流
--comment （Created→SubmittedForUnderwriting→ResultReceived→Issued）已全部落齐，出单业务其实是成功的。
--comment 故本缺陷表现为「写通读不通」：HTTP 轮询永远看不到出单完成，业务方无从得知保单已建。
--comment 列宽对齐核保域权威侧 50：AUW-/MUW- 前缀 + 36 位 UUID = 40，留 10 字符余量。
ALTER TABLE t_insurance_view    MODIFY COLUMN underwriting_id VARCHAR(50) COMMENT '核保单ID(承保依据溯源)';
ALTER TABLE t_issuance_progress MODIFY COLUMN underwriting_id VARCHAR(50) COMMENT '核保单ID';
ALTER TABLE t_policy_view       MODIFY COLUMN underwriting_id VARCHAR(50) COMMENT '核保单号';

--rollback ALTER TABLE t_insurance_view MODIFY COLUMN underwriting_id VARCHAR(36) COMMENT '核保单ID(承保依据溯源)';
--rollback ALTER TABLE t_issuance_progress MODIFY COLUMN underwriting_id VARCHAR(36) COMMENT '核保单ID';
--rollback ALTER TABLE t_policy_view MODIFY COLUMN underwriting_id VARCHAR(36) COMMENT '核保单号';
