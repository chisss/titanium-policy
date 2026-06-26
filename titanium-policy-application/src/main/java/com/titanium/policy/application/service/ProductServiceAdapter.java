package com.titanium.policy.application.service;

import com.titanium.policy.service.ProductServicePort;
import com.titanium.product.api.dto.ProductDTO;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 产品服务适配器
 * 实现ProductServicePort接口，适配ProductService的调用
 */
@Slf4j
@Service
public class ProductServiceAdapter implements ProductServicePort {

    @Resource
    private ProductService productService;

    @Override
    public Object getProductById(String productId, String tenantId) {
        return productService.getProductById(productId, tenantId);
    }

    @Override
    public Object getIssuanceConfig(String productId, String tenantId) {
        return productService.getIssuanceConfig(productId, tenantId);
    }

    @Override
    public Object getUnderwritingConfig(String productId, String tenantId) {
        return productService.getUnderwritingConfig(productId, tenantId);
    }

    @Override
    public Object getPolicyFormConfig(String productId, String tenantId) {
        return productService.getPolicyFormConfig(productId, tenantId);
    }
}