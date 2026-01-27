-- 初始化保单域数据库表结构

-- 创建投保意向单表
drop table if exists t_proposal;
create table t_proposal (
    proposal_id varchar(36) not null,
    proposal_no varchar(20) not null,
    policy_form varchar(10) not null,
    parent_proposal_id varchar(36),
    channel varchar(20) not null,
    customer_id varchar(36) not null,
    intended_sum_insured decimal(18,2) not null,
    intended_premium decimal(18,2) not null,
    currency varchar(3) not null,
    insurance_period_start datetime not null,
    insurance_period_end datetime not null,
    expected_product_code varchar(20) not null,
    status_code varchar(20) not null,
    status_time datetime not null,
    change_reason varchar(100),
    create_time datetime not null,
    update_time datetime not null,
    tenant_id varchar(36) not null,
    primary key (proposal_id),
    unique key uk_proposal_no (proposal_no),
    index idx_proposal_tenant (tenant_id),
    index idx_proposal_customer (customer_id)
);

-- 创建投保意向单投保人表
drop table if exists t_proposal_holder;
create table t_proposal_holder (
    holder_id varchar(36) not null,
    proposal_id varchar(36) not null,
    name varchar(50) not null,
    cert_type varchar(10) not null,
    cert_no varchar(20) not null,
    phone varchar(15),
    is_insured boolean not null,
    tenant_id varchar(36) not null,
    primary key (holder_id),
    foreign key fk_proposal_holder_proposal (proposal_id) references t_proposal(proposal_id),
    index idx_proposal_holder_proposal (proposal_id),
    index idx_proposal_holder_tenant (tenant_id)
);

-- 创建投保意向单标的表
drop table if exists t_proposal_subject;
create table t_proposal_subject (
    subject_id varchar(36) not null,
    proposal_id varchar(36) not null,
    subject_type varchar(20) not null,
    simple_info varchar(100) not null,
    estimated_risk_level varchar(2) not null,
    tenant_id varchar(36) not null,
    primary key (subject_id),
    foreign key fk_proposal_subject_proposal (proposal_id) references t_proposal(proposal_id),
    index idx_proposal_subject_proposal (proposal_id),
    index idx_proposal_subject_tenant (tenant_id)
);

-- 创建投保单表
drop table if exists t_insurance;
create table t_insurance (
                             insurance_id varchar(36) not null,
                             insurance_no varchar(20) not null,
    proposal_id varchar(36),
    policy_form varchar(10) not null,
    parent_insurance_id varchar(36),
    holder_id varchar(36) not null,
    insured_count int not null,
    exact_premium decimal(18,2) not null,
    currency varchar(3) not null,
    insurance_period_start datetime not null,
    insurance_period_end datetime not null,
    underwriting_priority int not null,
    underwriting_id varchar(36),
    underwriting_result_code varchar(20),
    underwriting_opinion varchar(500),
    underwriter_id varchar(36),
    underwriting_time datetime,
    underwriting_condition varchar(200),
    status_code varchar(20) not null,
    status_time datetime not null,
    change_reason varchar(100),
    create_time datetime not null,
    update_time datetime not null,
    tenant_id varchar(36) not null,
    primary key (insurance_id),
        unique key uk_application_no (insurance_no),
    index idx_application_proposal (proposal_id),
    index idx_application_tenant (tenant_id),
    index idx_application_applicant (holder_id)
);

-- 创建投保单险种表
drop table if exists t_insurance_product;
create table t_insurance_product (
    product_id varchar(36) not null,
    insurance_id varchar(36) not null,
    product_code varchar(20) not null,
    product_name varchar(50) not null,
    sum_insured decimal(18,2) not null,
    sum_insured_currency varchar(3) not null,
    premium_factor double not null,
    is_main_line boolean not null,
    tenant_id varchar(36) not null,
    primary key (product_id),
    foreign key fk_product_insurance (insurance_id) references t_insurance(insurance_id),
    index idx_product_insurance (insurance_id),
    index idx_product_tenant (tenant_id)
);

-- 创建投保单参与方表
drop table if exists t_insurance_party;
create table t_insurance_party (
    list_id varchar(36) not null,
    insurance_id varchar(36) not null,
    insurance_info json not null,
    insured_list json not null,
    beneficiary_list json,
    tenant_id varchar(36) not null,
    primary key (list_id),
    foreign key fk_insurance_party_insurance (insurance_id) references t_insurance(insurance_id),
    index idx_insurance_party_insurance (insurance_id),
    index idx_insurance_party_tenant (tenant_id)
);

-- 创建保单标的表
drop table if exists t_policy_subject;
create table t_policy_subject (
    subject_id varchar(36) not null,
    policy_id varchar(36) not null,
    subject_type varchar(20) not null,
    detail_info varchar(200) not null,
    risk_level varchar(2) not null,
    qualified_status boolean not null,
    tenant_id varchar(36) not null,
    primary key (subject_id),
    foreign key fk_policy_subject_policy (policy_id) references t_policy(policy_id),
    index idx_policy_subject_policy (policy_id),
    index idx_policy_subject_tenant (tenant_id)
);

-- 创建保单保障责任表
drop table if exists t_policy_coverage;
create table t_policy_coverage (
                                   coverage_id varchar(36) not null,
                                   product_id varchar(36) not null,
                                   coverage_code varchar(20) not null,
                                   coverage_name varchar(50) not null,
                                   coverage_sum_insured decimal(18,2) not null,
                                   coverage_currency varchar(3) not null,
                                   indemnity_condition varchar(200) not null,
                                   exclusion_clause varchar(500),
                                   indemnity_ratio double not null,
                                   tenant_id varchar(36) not null,
                                   primary key (coverage_id),
                                   index idx_policy_coverage_product (product_id),
                                   index idx_policy_coverage_tenant (tenant_id)
);


drop table if exists t_policy_item;
create table t_policy_item (
                               item_id varchar(36) not null,
                               coverage_id varchar(36) not null,
                               coverage json not null,
                               sum_insured decimal(18,2) not null,
                               sum_insured_currency varchar(3) not null,
                               premium decimal(18,2) not null,
                               premium_currency varchar(3) not null,
                               deductible int not null,
                               coinsurance int not null,
                               tenant_id varchar(36) not null,
                               primary key (item_id),
                               foreign key fk_policy_item_coverage (coverage_id) references t_policy_coverage(coverage_id),
                               index idx_policy_item_coverage (coverage_id),
                               index idx_policy_item_tenant (tenant_id)
);


-- 创建保单免赔规则表
drop table if exists t_policy_deductible;
create table t_policy_deductible (
    deductible_id varchar(36) not null,
    policy_id varchar(36) not null,
    deductible_type varchar(10) not null,
    deductible_value double not null,
    applicable_coverage varchar(200) not null,
    deductible_condition varchar(200),
    tenant_id varchar(36) not null,
    primary key (deductible_id),
    foreign key fk_policy_deductible_policy (policy_id) references t_policy(policy_id),
    index idx_policy_deductible_policy (policy_id),
    index idx_policy_deductible_tenant (tenant_id)
);

-- 创建保单缴费记录表
drop table if exists t_policy_payment;
create table t_policy_payment (
    payment_id varchar(36) not null,
    policy_id varchar(36) not null,
    payment_no varchar(20) not null,
    payment_amount decimal(18,2) not null,
    payment_currency varchar(3) not null,
    payment_time datetime not null,
    payment_method varchar(20) not null,
    reconciliation_status varchar(20) not null,
    tenant_id varchar(36) not null,
    primary key (payment_id),
    unique key uk_policy_payment_no (payment_no),
    foreign key fk_policy_payment_policy (policy_id) references t_policy(policy_id),
    index idx_policy_payment_policy (policy_id),
    index idx_policy_payment_tenant (tenant_id)
);

-- 创建保单单证表
drop table if exists t_policy_document;
create table t_policy_document (
    doc_id varchar(36) not null,
    policy_id varchar(36) not null,
    electronic_doc_no varchar(20) not null,
    paper_doc_no varchar(20),
    doc_generate_time datetime not null,
    signature_status varchar(20) not null,
    doc_storage_url varchar(200) not null,
    tenant_id varchar(36) not null,
    primary key (doc_id),
    unique key uk_policy_document_electronic (electronic_doc_no),
    unique key uk_policy_document_paper (paper_doc_no),
    foreign key fk_policy_document_policy (policy_id) references t_policy(policy_id),
    index idx_policy_document_policy (policy_id),
    index idx_policy_document_tenant (tenant_id)
);

-- 创建保单保费计划表
drop table if exists t_policy_premium_plan;
create table t_policy_premium_plan (
    plan_id varchar(36) not null,
    policy_id varchar(36) not null,
    premium_amount decimal(18,2) not null,
    currency varchar(3) not null,
    payment_method varchar(20) not null,
    payment_cycle varchar(20) not null,
    premium_due_date datetime not null,
    payment_status varchar(20) not null,
    tenant_id varchar(36) not null,
    primary key (plan_id),
    foreign key fk_policy_premium_plan_policy (policy_id) references t_policy(policy_id),
    index idx_policy_premium_plan_policy (policy_id),
    index idx_policy_premium_plan_tenant (tenant_id)
);