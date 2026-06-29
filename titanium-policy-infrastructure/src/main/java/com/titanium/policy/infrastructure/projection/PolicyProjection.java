package com.titanium.policy.infrastructure.projection;

import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import com.titanium.policy.event.PolicyActivatedEvent;
import com.titanium.policy.event.PolicyCancelledEvent;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.event.PolicyExpiredEvent;
import com.titanium.policy.event.PolicyResumedEvent;
import com.titanium.policy.event.PolicySuspendedEvent;
import com.titanium.policy.infrastructure.entity.PolicyEntity;
import com.titanium.policy.infrastructure.repository.jpa.JpaPolicyRepository;
import com.titanium.policy.query.PolicyQuery;

import lombok.RequiredArgsConstructor;

/**
 * 保单投影类，用于处理保单领域事件并更新数据库
 */
@Component
@RequiredArgsConstructor
public class PolicyProjection {

    private final JpaPolicyRepository policyRepository;

    /**
     * 处理保单创建事件
     */
    @EventHandler
    public void handle(PolicyCreatedEvent event) {
        PolicyEntity entity = new PolicyEntity();
        policyRepository.save(entity);
    }

    /**
     * 处理保单激活事件
     */
    @EventHandler
    public void handle(PolicyActivatedEvent event) {
        PolicyEntity entity = new PolicyEntity();
        policyRepository.save(entity);
    }

    /**
     * 处理保单暂停事件
     */
    @EventHandler
    public void handle(PolicySuspendedEvent event) {
        PolicyEntity entity = new PolicyEntity();
        policyRepository.save(entity);
    }

    /**
     * 处理保单恢复事件
     */
    @EventHandler
    public void handle(PolicyResumedEvent event) {
        PolicyEntity entity = new PolicyEntity();
        policyRepository.save(entity);
    }

    /**
     * 处理保单取消事件
     */
    @EventHandler
    public void handle(PolicyCancelledEvent event) {
        PolicyEntity entity = new PolicyEntity();
        policyRepository.save(entity);
    }

    /**
     * 处理保单过期事件
     */
    @EventHandler
    public void handle(PolicyExpiredEvent event) {
        PolicyEntity entity = new PolicyEntity();
        policyRepository.save(entity);
    }

    /**
     * 处理保单查询
     */
    @QueryHandler
    public PolicyEntity handle(PolicyQuery query) {
        return policyRepository.findByPolicyNoAndTenantId(query.policyId(), query.tenantId()).orElse(null);
    }
}
