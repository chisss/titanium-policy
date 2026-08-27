--liquibase formatted sql

--changeset weisun:policy-product-pricing-plan-version-202608241600
-- 冻结出单确认保费使用的 Product 定价计划版本，供保全建案锁定权威产品基准。
ALTER TABLE t_policy_product
    ADD COLUMN pricing_plan_version VARCHAR(64) NULL COMMENT '确认保费使用的定价计划版本' AFTER product_version;

--rollback ALTER TABLE t_policy_product DROP COLUMN pricing_plan_version;
