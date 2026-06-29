package com.titanium.policy.service;

import java.util.List;

/**
 * 核保服务端口
 * 定义核保服务的接口，由应用层实现
 */
public interface UnderwritingServicePort {
    /**
     * 创建核保
     */
    Object createUnderwriting(Object request, String tenantId);

    /**
     * 根据ID查询核保
     */
    Object getUnderwritingById(String underwritingId, String tenantId);

    /**
     * 根据保单ID查询核保
     */
    List<?> getUnderwritingByPolicyId(String policyId, String tenantId);

    /**
     * 执行核保
     */
    Object underwrite(String underwritingId, Object request, String tenantId);
}
