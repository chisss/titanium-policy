package com.titanium.policy.service;

import com.titanium.policy.aggregate.Policy;
import com.titanium.metadata.enums.PolicyEnum.PolicyStatus;

/**
 * 保单领域服务
 */
public interface PolicyService {
    
    /**
     * 检查保单是否可以激活
     * @param policy 保单对象
     * @return 是否可以激活
     */
    boolean canActivate(Policy policy);
    
    /**
     * 检查保单是否已过期
     * @param policy 保单对象
     * @return 是否已过期
     */
    boolean isExpired(Policy policy);
    
    /**
     * 计算保单的保费
     * @param policy 保单对象
     * @return 保费金额
     */
    Policy calculatePremium(Policy policy);
    
    /**
     * 验证保单数据的完整性
     * @param policy 保单对象
     * @return 是否完整
     */
    boolean validatePolicyData(Policy policy);
    
    /**
     * 更新保单状态
     * @param policy 保单对象
     * @param newStatus 新状态
     */
    void updatePolicyStatus(Policy policy, PolicyStatus newStatus);
}