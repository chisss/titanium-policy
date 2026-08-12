--liquibase formatted sql

--changeset weisun:policy-beneficiary-customer-id-nullable
-- 受益人客户ID 放开非空约束：受益人未必是本平台注册客户——投保时常只提供「姓名 + 证件类型 + 证件号」
-- 而不建客户档案（如指定未成年子女、父母为身故受益人）。原 NOT NULL 约束导致此类保单的参与方投影
-- 插入失败（Column 'customer_id' cannot be null），读模型永久缺失受益人数据。
ALTER TABLE t_policy_beneficiary
    MODIFY COLUMN customer_id VARCHAR(36) NULL COMMENT '受益人客户ID(可空:受益人未必是注册客户,身份以姓名+证件承载)';
--rollback ALTER TABLE t_policy_beneficiary MODIFY COLUMN customer_id VARCHAR(36) NOT NULL COMMENT '受益人客户ID';
