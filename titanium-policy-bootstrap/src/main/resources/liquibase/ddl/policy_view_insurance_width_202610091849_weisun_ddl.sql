--liquibase formatted sql
--changeset weisun:policy-view-insurance-width-20261009
-- G10/g10-02：列宽统一——险种三级分类列 64 → 32（三级码最长 PROFESSIONAL_LIABILITY=23）。
-- 覆盖 4 处实读列：policy_view_...sql:202-204 三张读模型表 + policy_multiline_...sql:41 t_policy_product
-- （文档只列前三处，第四处为实读补充，同语义同批收敛）。原注释文本原样保留（MODIFY 不重写注释会丢失）。
-- ⚠ 收窄前置：库内实际值须 ≤32 字符（由 g10-03 对账 SQL ① 验证；若存在超长脏值须先回滚本 changeset）。
ALTER TABLE t_proposal_view  MODIFY COLUMN insurance_type VARCHAR(32) NULL COMMENT '险种三级分类(TERM_LIFE/WHOLE_LIFE/ENDOWMENT/ANNUITY等)';
ALTER TABLE t_insurance_view MODIFY COLUMN insurance_type VARCHAR(32) NULL COMMENT '险种三级分类(TERM_LIFE/WHOLE_LIFE/ENDOWMENT/ANNUITY等)';
ALTER TABLE t_policy_view    MODIFY COLUMN insurance_type VARCHAR(32) NULL COMMENT '险种三级分类(TERM_LIFE/WHOLE_LIFE/ENDOWMENT/ANNUITY等)';
ALTER TABLE t_policy_product MODIFY COLUMN insurance_type VARCHAR(32) NULL COMMENT '险种三级分类';
--rollback ALTER TABLE t_proposal_view MODIFY COLUMN insurance_type VARCHAR(64) NULL COMMENT '险种三级分类(TERM_LIFE/WHOLE_LIFE/ENDOWMENT/ANNUITY等)'; ALTER TABLE t_insurance_view MODIFY COLUMN insurance_type VARCHAR(64) NULL COMMENT '险种三级分类(TERM_LIFE/WHOLE_LIFE/ENDOWMENT/ANNUITY等)'; ALTER TABLE t_policy_view MODIFY COLUMN insurance_type VARCHAR(64) NULL COMMENT '险种三级分类(TERM_LIFE/WHOLE_LIFE/ENDOWMENT/ANNUITY等)'; ALTER TABLE t_policy_product MODIFY COLUMN insurance_type VARCHAR(64) NULL COMMENT '险种三级分类';
