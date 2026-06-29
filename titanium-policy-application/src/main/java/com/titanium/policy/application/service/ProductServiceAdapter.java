package com.titanium.policy.application.service;

import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import com.titanium.metadata.enums.product.ProductEnum;
import com.titanium.policy.service.ProductServicePort;

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
    public ProductEnum.IssuanceMode getIssuanceMode(String productId, String tenantId) {
        Object config = productService.getIssuanceConfig(productId, tenantId);
        if (config == null) {
            throw new IllegalStateException(String.format("产品[%s]未配置出单流程，无法决定出单模式", productId));
        }
        // 弱类型配置经 Feign 跨进程返回为 Map/JSON，出单模式的解析细节封装在适配器（基础设施关注点），
        // 领域侧只依赖 ProductServicePort 返回的类型化枚举
        JSONObject json = config instanceof JSONObject jo ? jo : JSON.parseObject(JSON.toJSONString(config));
        String mode = json.getString("issuanceMode");
        if (mode == null || mode.isBlank()) {
            throw new IllegalStateException(String.format("产品[%s]出单配置缺少 issuanceMode 字段", productId));
        }
        try {
            return ProductEnum.IssuanceMode.valueOf(mode);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(String.format("产品[%s]出单模式取值非法: %s", productId, mode), e);
        }
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
