--liquibase formatted sql

--changeset weisun:policy-projection-id-length-fix
-- 险种段族读模型主键由「policyId(36) + _ + policyProductId(36) [+ _ + subjectId/clauseId/coverageCode]」派生，
-- 拼接后最长达 110 字符，原 VARCHAR(64) 导致投影 INSERT 超长失败（Data too long for column 'id'），
-- 使 policy-query-group 整批事务回滚、token 卡死、全部保单读模型永久缺失。统一放宽至 128。
ALTER TABLE t_policy_product MODIFY COLUMN id VARCHAR(128) NOT NULL COMMENT '险种段行主键(policyId_policyProductId 派生)';
ALTER TABLE t_policy_subject MODIFY COLUMN id VARCHAR(128) NOT NULL COMMENT '标的行主键(policyId_policyProductId_subjectId 派生)';
ALTER TABLE t_policy_clause  MODIFY COLUMN id VARCHAR(128) NOT NULL COMMENT '条款行主键(policyId_policyProductId_clauseId 派生)';
ALTER TABLE t_policy_coverage MODIFY COLUMN id VARCHAR(128) NOT NULL COMMENT '责任行主键(policyId_policyProductId_coverageCode 派生)';
--rollback ALTER TABLE t_policy_product MODIFY COLUMN id VARCHAR(64) NOT NULL;
--rollback ALTER TABLE t_policy_subject MODIFY COLUMN id VARCHAR(64) NOT NULL;
--rollback ALTER TABLE t_policy_clause MODIFY COLUMN id VARCHAR(64) NOT NULL;
--rollback ALTER TABLE t_policy_coverage MODIFY COLUMN id VARCHAR(64) NOT NULL;

--changeset weisun:policy-insured-customer-id-nullable
-- 被保险人客户ID 放开非空约束：与受益人同理，被保险人未必是本平台注册客户（如为家庭成员投保时
-- 仅提供姓名+证件），原 NOT NULL 导致参与方投影插入失败（Column 'customer_id' cannot be null）。
ALTER TABLE t_policy_insured
    MODIFY COLUMN customer_id VARCHAR(36) NULL COMMENT '被保险人客户ID(可空:被保险人未必是注册客户,身份以姓名+证件承载)';
--rollback ALTER TABLE t_policy_insured MODIFY COLUMN customer_id VARCHAR(36) NOT NULL COMMENT '被保险人客户ID';
