package com.titanium.policy.service;

/**
 * 批单号生成器（领域端口）
 * <p>
 * 由应用层实现，生成 policy 域批改的业务凭证号。批改回写命令下发前预生成，确保命令幂等可追溯。
 * </p>
 */
public interface EndorsementNoGenerator {

    /**
     * 生成批单号
     *
     * @param policyId 保单ID
     * @return 批单号
     */
    String generate(String policyId);
}
