package com.titanium.policy.service;

import com.titanium.metadata.enums.product.ProductEnum;

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
     * 获取产品配置的出单模式（产品驱动出单的类型化契约）
     * <p>
     * 取代调用方硬编码出单步数：由产品域配置决定该产品走一步/两步/三步出单。
     * 弱类型 {@link #getIssuanceConfig} 的解析细节封装在适配器，领域侧只依赖此类型化结果。
     * </p>
     *
     * @param productId 产品ID
     * @param tenantId 租户ID
     * @return 出单模式
     */
    ProductEnum.IssuanceMode getIssuanceMode(String productId, String tenantId);

    /**
     * 获取产品核保配置
     */
    Object getUnderwritingConfig(String productId, String tenantId);

    /**
     * 获取产品保单形态配置
     */
    Object getPolicyFormConfig(String productId, String tenantId);
}
