--liquibase formatted sql

--changeset weisun:policy-payment-method-202609111900
-- 保单读模型补「缴费方式」列（趸缴/期缴）。
-- 背景：m2-907 新增 policy.payment.method 字段执行器后，缴费方式已能真实写入 Policy 聚合，
-- 但读模型无对应列，投影无落点 —— 写通读不通（缺口登记 G7）。
-- 🔴 与 collection_mode（收费方式：线下/线上/免费/先用后付/代扣）是两个维度，勿混用。
ALTER TABLE t_policy_view
    ADD COLUMN payment_method VARCHAR(32) NULL COMMENT '缴费方式码(SINGLE_PAYMENT趸缴/INSTALLMENT_PAYMENT期缴)' AFTER collection_mode;

--rollback ALTER TABLE t_policy_view DROP COLUMN payment_method;
