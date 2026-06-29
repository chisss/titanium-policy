package com.titanium.policy.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.titanium.metadata.enums.policy.PolicyEnum.PolicyStatus;
import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.aggregate.Policy;
import com.titanium.policy.service.PolicyService;

@Service
public class PolicyServiceImpl implements PolicyService {

    @Override
    public boolean canActivate(Policy policy) {
        if (policy == null) {
            return false;
        }

        // 只有待激活状态的保单可以激活
        if (!Objects.equals(policy.getStatus().statusCode().getCode(), PolicyStatus.PENDING_EFFECTIVE.getCode())) {
            return false;
        }

        // 确保生效日期不晚于当前日期
        LocalDateTime now = LocalDateTime.now();
        return !policy.getBasicInfo().insurancePeriodStart().isAfter(now);
    }

    @Override
    public boolean isExpired(Policy policy) {
        if (policy == null) {
            return false;
        }

        // 如果保单已经被取消，不算过期
        if (Objects.equals(policy.getStatus().statusCode().getCode(), PolicyStatus.CANCELLED.getCode())) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        return policy.getBasicInfo().insurancePeriodEnd().isBefore(now);
    }

    @Override
    public Policy calculatePremium(Policy policy) {
        if (policy == null || policy.getInsuranceProducts() == null || policy.getInsuranceProducts().isEmpty()) {
            return policy;
        }

        // 计算所有保单项的保费总和
        Money totalPremium = policy.getInsuranceProducts().stream().map(item -> item.premium())
                .reduce(Money.zero("CNY"), Money::add);

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
                || Objects.isNull(policy.getStatus()) || Objects.isNull(policy.getTenantId())) {
            return false;
        }

        // 验证生效日期必须早于过期日期
        if (!policy.getBasicInfo().insurancePeriodStart().isBefore(policy.getBasicInfo().insurancePeriodEnd())) {
            return false;
        }

        // 验证保费必须大于0
        if (policy.getBasicInfo().totalPremium().value().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        // 验证保单项
        if (policy.getInsuranceProducts() != null) {
            for (var item : policy.getInsuranceProducts()) {
                if (Objects.isNull(item.productId()) || Objects.isNull(item.coverages())
                        || Objects.isNull(item.sumInsured()) || Objects.isNull(item.premium())) {
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
        if (Objects.equals(policy.getStatus().statusCode().getCode(), PolicyStatus.CANCELLED.getCode())
                && newStatus != PolicyStatus.CANCELLED) {
            throw new IllegalArgumentException("已取消的保单不能改变状态");
        }

        if (Objects.equals(policy.getStatus().statusCode().getCode(), PolicyStatus.EXPIRED.getCode())
                && newStatus != PolicyStatus.EXPIRED) {
            throw new IllegalArgumentException("已过期的保单不能改变状态");
        }

        // 根据新状态调用聚合根的公开状态转换方法
        // 注：Policy 为 Axon 聚合根，无参状态方法(cancel/suspend/activate)不存在，
        // 统一通过聚合根公开的 updatePolicyStatus(StatusCode, reason, operator) 驱动状态机
        final String operator = "POLICY_DOMAIN_SERVICE";
        switch (newStatus) {
            case CANCELLED:
                policy.updatePolicyStatus(
                        com.titanium.policy.valueobject.PolicyStatus.StatusCode.CANCELLED, "保单作废", operator);
                break;
            case EXPIRED:
                policy.expire();
                break;
            case SUSPENDED:
                policy.updatePolicyStatus(
                        com.titanium.policy.valueobject.PolicyStatus.StatusCode.SUSPENDED, "保单暂停", operator);
                break;
            case EFFECTIVE:
                policy.updatePolicyStatus(
                        com.titanium.policy.valueobject.PolicyStatus.StatusCode.EFFECTIVE, "保单生效", operator);
                break;
            case PENDING_EFFECTIVE:
                // 待处理状态不需要特殊方法
                break;
            default:
                // TERMINATED/LAPSED 等其余状态当前不由本服务驱动
                break;
        }
    }
}
