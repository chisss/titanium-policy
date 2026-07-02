package com.titanium.policy.infrastructure.adapter;

import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import com.titanium.metadata.enums.product.ProductEnum;
import com.titanium.policy.port.ProductServicePort;
import com.titanium.product.api.ProductApi;
import com.titanium.product.api.dto.ProductDTO;
import com.titanium.product.api.response.ApiResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 产品服务适配器
 * <p>
 * {@link ProductServicePort} 的基础设施实现，直接调用产品域 {@link ProductApi}（Feign）并解包
 * {@link ApiResponse}，将跨进程弱类型配置解析为领域侧需要的类型化结果（六边形架构 Adapter）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductServiceAdapter implements ProductServicePort {

    private final ProductApi productApi;

    @Override
    public Object getProductById(String productId, String tenantId) {
        log.info("获取产品详情, productId={}, tenantId={}", productId, tenantId);
        ApiResponse<ProductDTO> response = productApi.getProductById(productId, tenantId);
        if (response.isSuccess()) {
            return response.getData();
        }
        log.error("获取产品详情失败, productId={}, error={}", productId, response.getMessage());
        throw new RuntimeException("获取产品详情失败: " + response.getMessage());
    }

    @Override
    public Object getIssuanceConfig(String productId, String tenantId) {
        log.info("获取产品出单流程配置, productId={}, tenantId={}", productId, tenantId);
        ApiResponse<Object> response = productApi.getIssuanceConfig(productId, tenantId);
        if (response.isSuccess()) {
            return response.getData();
        }
        log.error("获取产品出单流程配置失败, productId={}, error={}", productId, response.getMessage());
        throw new RuntimeException("获取产品出单流程配置失败: " + response.getMessage());
    }

    @Override
    public ProductEnum.IssuanceMode getIssuanceMode(String productId, String tenantId) {
        Object config = getIssuanceConfig(productId, tenantId);
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
        log.info("获取产品核保配置, productId={}, tenantId={}", productId, tenantId);
        ApiResponse<Object> response = productApi.getUnderwritingConfig(productId, tenantId);
        if (response.isSuccess()) {
            return response.getData();
        }
        log.error("获取产品核保配置失败, productId={}, error={}", productId, response.getMessage());
        throw new RuntimeException("获取产品核保配置失败: " + response.getMessage());
    }

    @Override
    public Object getPolicyFormConfig(String productId, String tenantId) {
        log.info("获取产品保单形态配置, productId={}, tenantId={}", productId, tenantId);
        ApiResponse<Object> response = productApi.getPolicyFormConfig(productId, tenantId);
        if (response.isSuccess()) {
            return response.getData();
        }
        log.error("获取产品保单形态配置失败, productId={}, error={}", productId, response.getMessage());
        throw new RuntimeException("获取产品保单形态配置失败: " + response.getMessage());
    }
}
