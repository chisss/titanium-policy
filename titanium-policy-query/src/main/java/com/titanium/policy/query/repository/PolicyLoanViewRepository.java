package com.titanium.policy.query.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.titanium.policy.query.view.PolicyLoanView;

/**
 * 保单贷款明细读模型仓储（CQRS 读侧，g04-03）。
 * <p>
 * 映射 {@code t_policy_loan} 读模型表，由 {@code PolicyLoanProjectionEventHandler} 投影写入，
 * 命名 {@code XxxViewRepository} 与写侧事件溯源取数端口 {@code PolicyLoanAccountRepository}
 * 分立不撞名。Spring Data 接口，无需手写实现。
 * </p>
 */
public interface PolicyLoanViewRepository extends JpaRepository<PolicyLoanView, String> {
}
