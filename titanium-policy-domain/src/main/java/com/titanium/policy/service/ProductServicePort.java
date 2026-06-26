package com.titanium.policy.service;

/**
 * 产品服务端口
 * 定义产品服务的接口，由应用层实现
 */
public interface ProductServicePort {
    /**
     * 根据产品ID获取产品详情
     */
    Object getProductById(String productId, String tenantId);

    /**
     * 获取产品出单流程配置
     */
    Object getIssuanceConfig(String productId, String tenantId);

    /**
     * 获取产品核保配置
     */
    Object getUnderwritingConfig(String productId, String tenantId);

    /**
     * 获取产品保单形态配置
     */
    Object getPolicyFormConfig(String productId, String tenantId);
}