package com.titanium.policy.application.service;

import org.springframework.stereotype.Service;

import com.titanium.product.api.ProductApi;
import com.titanium.product.api.dto.ProductDTO;
import com.titanium.product.api.response.ApiResponse;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 产品服务客户端
 * 用于调用产品系统的API
 */
@Slf4j
@Service
public class ProductService {

    @Resource
    private ProductApi productApi;

    /**
     * 根据产品ID获取产品详情
     */
    public ProductDTO getProductById(String productId, String tenantId) {
        log.info("获取产品详情, productId={}, tenantId={}", productId, tenantId);
        ApiResponse<ProductDTO> response = productApi.getProductById(productId, tenantId);
        if (response.isSuccess()) {
            return response.getData();
        } else {
            log.error("获取产品详情失败, productId={}, error={}", productId, response.getMessage());
            throw new RuntimeException("获取产品详情失败: " + response.getMessage());
        }
    }

    /**
     * 获取产品出单流程配置
     */
    public Object getIssuanceConfig(String productId, String tenantId) {
        log.info("获取产品出单流程配置, productId={}, tenantId={}", productId, tenantId);
        ApiResponse<Object> response = productApi.getIssuanceConfig(productId, tenantId);
        if (response.isSuccess()) {
            return response.getData();
        } else {
            log.error("获取产品出单流程配置失败, productId={}, error={}", productId, response.getMessage());
            throw new RuntimeException("获取产品出单流程配置失败: " + response.getMessage());
        }
    }

    /**
     * 获取产品核保配置
     */
    public Object getUnderwritingConfig(String productId, String tenantId) {
        log.info("获取产品核保配置, productId={}, tenantId={}", productId, tenantId);
        ApiResponse<Object> response = productApi.getUnderwritingConfig(productId, tenantId);
        if (response.isSuccess()) {
            return response.getData();
        } else {
            log.error("获取产品核保配置失败, productId={}, error={}", productId, response.getMessage());
            throw new RuntimeException("获取产品核保配置失败: " + response.getMessage());
        }
    }

    /**
     * 获取产品保单形态配置
     */
    public Object getPolicyFormConfig(String productId, String tenantId) {
        log.info("获取产品保单形态配置, productId={}, tenantId={}", productId, tenantId);
        ApiResponse<Object> response = productApi.getPolicyFormConfig(productId, tenantId);
        if (response.isSuccess()) {
            return response.getData();
        } else {
            log.error("获取产品保单形态配置失败, productId={}, error={}", productId, response.getMessage());
            throw new RuntimeException("获取产品保单形态配置失败: " + response.getMessage());
        }
    }
}
