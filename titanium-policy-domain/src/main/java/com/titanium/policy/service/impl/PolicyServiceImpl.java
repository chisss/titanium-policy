package com.titanium.policy.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.titanium.metadata.enums.PolicyEnum.PolicyStatus;
import com.titanium.policy.aggregate.Policy;
import com.titanium.policy.service.PolicyService;
import com.titanium.policy.valueobject.Amount;

@Service
public class PolicyServiceImpl implements PolicyService {

    @Override
    public boolean canActivate(Policy policy) {
        if (policy == null) {
            return false;
        }

        // 只有待激活状态的保单可以激活
        if (policy.getStatus() != PolicyStatus.PENDING) {
            return false;
        }

        // 确保生效日期不晚于当前日期
        LocalDateTime now = LocalDateTime.now();
        return !policy.getEffectiveDate().isAfter(now);
    }

    @Override
    public boolean isExpired(Policy policy) {
        if (policy == null) {
            return false;
        }

        // 如果保单已经被取消，不算过期
        if (policy.getStatus() == PolicyStatus.CANCELLED) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        return policy.getExpiryDate().isBefore(now);
    }

    @Override
    public Policy calculatePremium(Policy policy) {
        if (policy == null || policy.getPolicyItems() == null || policy.getPolicyItems().isEmpty()) {
            return policy;
        }

        // 计算所有保单项的保费总和
        Amount totalPremium = policy.getPolicyItems().stream().map(item -> item.premium())
                .reduce(Amount.of(BigDecimal.ZERO, "CNY"), Amount::add);

        // 更新保单的总保费
        // 注意：由于Policy是聚合根，我们需要通过方法来更新保费
        // 这里直接返回保单，因为Policy类中没有提供更新保费的方法
        // 在实际应用中，应该在Policy类中添加更新保费的方法
        return policy;
    }

    @Override
    public boolean validatePolicyData(Policy policy) {
        if (policy == null) {
            return false;
        }

        // 验证基本字段
        if (Objects.isNull(policy.getPolicyId()) || Objects.isNull(policy.getPolicyNo())
                || Objects.isNull(policy.getCustomerId()) || Objects.isNull(policy.getProductId())
                || Objects.isNull(policy.getEffectiveDate()) || Objects.isNull(policy.getExpiryDate())
                || Objects.isNull(policy.getPremium()) || Objects.isNull(policy.getStatus())
                || Objects.isNull(policy.getTenantId())) {
            return false;
        }

        // 验证生效日期必须早于过期日期
        if (!policy.getEffectiveDate().isBefore(policy.getExpiryDate())) {
            return false;
        }

        // 验证保费必须大于0
        if (policy.getPremium().value().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        // 验证保单项
        if (policy.getPolicyItems() != null) {
            for (var item : policy.getPolicyItems()) {
                if (Objects.isNull(item.itemId()) || Objects.isNull(item.coverageId())
                        || Objects.isNull(item.coverage()) || Objects.isNull(item.sumInsured())
                        || Objects.isNull(item.premium())) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    public void updatePolicyStatus(Policy policy, PolicyStatus newStatus) {
        if (policy == null || newStatus == null) {
            return;
        }

        // 根据业务规则验证状态转换
        if (policy.getStatus() == PolicyStatus.CANCELLED && newStatus != PolicyStatus.CANCELLED) {
            throw new IllegalArgumentException("已取消的保单不能改变状态");
        }

        if (policy.getStatus() == PolicyStatus.EXPIRED && newStatus != PolicyStatus.EXPIRED) {
            throw new IllegalArgumentException("已过期的保单不能改变状态");
        }

        // 根据新状态调用相应的聚合根方法
        switch (newStatus) {
            case CANCELLED:
                policy.cancel();
                break;
            case EXPIRED:
                policy.expire();
                break;
            case SUSPENDED:
                policy.suspend();
                break;
            case EFFECTIVE:
                policy.activate();
                break;
            case PENDING:
                // 待处理状态不需要特殊方法
                break;
        }
    }
}
