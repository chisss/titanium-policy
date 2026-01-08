package com.titanium.policy.aggregate;

import java.time.LocalDateTime;
import java.util.List;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import com.titanium.metadata.enums.PolicyEnum.PolicyStatus;
import com.titanium.policy.command.ActivatePolicyCommand;
import com.titanium.policy.command.CreatePolicyCommand;
import com.titanium.policy.entity.PolicyItem;
import com.titanium.policy.event.PolicyActivatedEvent;
import com.titanium.policy.event.PolicyCancelledEvent;
import com.titanium.policy.event.PolicyCreatedEvent;
import com.titanium.policy.event.PolicyExpiredEvent;
import com.titanium.policy.event.PolicyResumedEvent;
import com.titanium.policy.event.PolicySuspendedEvent;
import com.titanium.policy.valueobject.Amount;
import com.titanium.policy.valueobject.PolicyNo;

import lombok.Getter;

/**
 * 保单聚合根【保险核心领域模型】
 * <p>
 * 承载保险保单的全生命周期管理，是保单领域的核心聚合根，聚合边界包含PolicyItem子实体、Amount/PolicyNo值对象
 * </p>
 *
 * @since 1.0.0（保单领域v1版本）
 * @note 核心业务规则： 1. 聚合唯一标识：policyId（Axon聚合根标识），policyNo为业务唯一保单编号； 2.
 *       状态流转规则：仅PENDING（待处理）状态的保单可激活为ACTIVE（已激活），不允许逆向流转； 3.
 *       多租户隔离：通过tenantId区分不同租户的保单数据，避免数据混叠；
 */
@Aggregate
@Getter
public class Policy {
    @AggregateIdentifier // Axon注解：标记聚合根的唯一标识（类似DDD的聚合根ID）
    private String           policyId;      // 聚合根唯一ID（技术标识）
    private PolicyNo         policyNo;      // 保单编号（值对象：业务唯一标识，包含编号格式校验）
    private String           customerId;    // 客户ID（关联客户聚合根）
    private String           productId;     // 产品ID（关联保险产品聚合根）
    private LocalDateTime    effectiveDate; // 保单生效时间
    private LocalDateTime    expiryDate;    // 保单过期时间
    private Amount           premium;       // 保费（值对象：包含金额+币种，精度校验）
    private PolicyStatus     status;        // 保单状态（枚举：约束状态流转）
    private List<PolicyItem> policyItems;   // 保单项（子实体：不可脱离Policy独立存在）
    private String           tenantId;      // 租户ID（多租户隔离）

    @CommandHandler
    public Policy(CreatePolicyCommand command) {
        // 参数验证
        if (command.policyId() == null || command.policyId().isEmpty()) {
            throw new IllegalArgumentException("Policy ID cannot be empty");
        }
        if (command.policyNo() == null) {
            throw new IllegalArgumentException("Policy number cannot be null");
        }
        if (command.effectiveDate().isAfter(command.expiryDate())) {
            throw new IllegalArgumentException("Effective date must be before expiry date");
        }
        AggregateLifecycle.apply(new PolicyCreatedEvent(command.policyId(), command.policyNo(), command.customerId(),
                command.productId(), command.effectiveDate(), command.expiryDate(), command.premium(),
                PolicyStatus.PENDING, command.policyItems(), command.tenantId()));
    }

    @CommandHandler
    public void handle(ActivatePolicyCommand command) {
        activate();
    }

    public void activate() {
        if (this.status != PolicyStatus.PENDING) {
            throw new IllegalArgumentException("Only pending policies can be activated");
        }
        if (this.effectiveDate.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Policy cannot be activated before effective date");
        }
        AggregateLifecycle.apply(new PolicyActivatedEvent(this.policyId, this.effectiveDate, this.tenantId));
    }

    public void cancel() {
        if (this.status == PolicyStatus.CANCELLED) {
            throw new IllegalArgumentException("Policy is already cancelled");
        }
        if (this.status == PolicyStatus.EXPIRED) {
            throw new IllegalArgumentException("Expired policies cannot be cancelled");
        }
        AggregateLifecycle.apply(new PolicyCancelledEvent(this.policyId, this.expiryDate, this.tenantId));
    }

    public void expire() {
        if (this.status == PolicyStatus.EXPIRED) {
            throw new IllegalArgumentException("Policy is already expired");
        }
        if (this.status == PolicyStatus.CANCELLED) {
            throw new IllegalArgumentException("Cancelled policies cannot expire");
        }
        AggregateLifecycle.apply(new PolicyExpiredEvent(this.policyId, this.expiryDate, this.tenantId));
    }

    public void suspend() {
        if (this.status != PolicyStatus.EFFECTIVE) {
            throw new IllegalArgumentException("Only effective policies can be suspended");
        }
        AggregateLifecycle.apply(new PolicySuspendedEvent(this.policyId, this.expiryDate, this.tenantId));
    }

    public void resume() {
        if (this.status != PolicyStatus.SUSPENDED) {
            throw new IllegalArgumentException("Only suspended policies can be resumed");
        }
        AggregateLifecycle.apply(new PolicyResumedEvent(this.policyId, this.expiryDate, this.tenantId));
    }

    public boolean isExpired() {
        return this.status == PolicyStatus.EXPIRED || this.expiryDate.isBefore(LocalDateTime.now());
    }

    public boolean canActivate() {
        return this.status == PolicyStatus.PENDING && !this.effectiveDate.isAfter(LocalDateTime.now());
    }

    public boolean validateData() {
        return this.policyId != null && this.policyNo != null && this.customerId != null && this.productId != null
                && this.effectiveDate != null && this.expiryDate != null && this.premium != null && this.status != null
                && this.tenantId != null && this.effectiveDate.isBefore(this.expiryDate);
    }

    @EventSourcingHandler
    protected void on(PolicyCreatedEvent event) {
        this.policyId = event.policyId();
        this.policyNo = event.policyNo();
        this.customerId = event.customerId();
        this.productId = event.productId();
        this.effectiveDate = event.effectiveDate();
        this.expiryDate = event.expiryDate();
        this.premium = event.premium();
        this.status = event.status();
        this.policyItems = event.policyItems();
        this.tenantId = event.tenantId();
    }

    @EventSourcingHandler
    protected void on(PolicyActivatedEvent event) {
        this.status = PolicyStatus.EFFECTIVE;
        this.expiryDate = event.activatedAt();
    }

    @EventSourcingHandler
    protected void on(PolicyCancelledEvent event) {
        this.status = PolicyStatus.CANCELLED;
    }

    @EventSourcingHandler
    protected void on(PolicyExpiredEvent event) {
        this.status = PolicyStatus.EXPIRED;
    }

    @EventSourcingHandler
    protected void on(PolicySuspendedEvent event) {
        this.status = PolicyStatus.SUSPENDED;
    }

    @EventSourcingHandler
    protected void on(PolicyResumedEvent event) {
        this.status = PolicyStatus.EFFECTIVE;
    }

    protected Policy() {
        // Required by Axon Framework
    }
}
