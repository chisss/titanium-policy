package com.titanium.policy.application.service;

import com.titanium.underwriting.api.UnderwritingApi;
import com.titanium.underwriting.api.dto.UnderwritingDTO;
import com.titanium.underwriting.api.request.CreateUnderwritingRequest;
import com.titanium.underwriting.api.request.UnderwriteRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 核保服务客户端
 * 用于调用核保系统的API
 */
@Slf4j
@Service
public class UnderwritingService {

    @Resource
    private UnderwritingApi underwritingApi;

    /**
     * 创建核保
     */
    public UnderwritingDTO createUnderwriting(CreateUnderwritingRequest request, String tenantId) {
        log.info("创建核保, tenantId={}", tenantId);
        ResponseEntity<UnderwritingDTO> response = underwritingApi.createUnderwriting(request, tenantId);
        return response.getBody();
    }

    /**
     * 根据ID查询核保
     */
    public UnderwritingDTO getUnderwritingById(String underwritingId, String tenantId) {
        log.info("获取核保详情, underwritingId={}, tenantId={}", underwritingId, tenantId);
        ResponseEntity<UnderwritingDTO> response = underwritingApi.getUnderwritingById(underwritingId, tenantId);
        return response.getBody();
    }

    /**
     * 根据保单ID查询核保
     */
    public List<UnderwritingDTO> getUnderwritingByPolicyId(String policyId, String tenantId) {
        log.info("根据保单ID获取核保, policyId={}, tenantId={}", policyId, tenantId);
        ResponseEntity<List<UnderwritingDTO>> response = underwritingApi.getUnderwritingByPolicyId(policyId, tenantId);
        return response.getBody();
    }

    /**
     * 执行核保
     */
    public UnderwritingDTO underwrite(String underwritingId, UnderwriteRequest request, String tenantId) {
        log.info("执行核保, underwritingId={}, tenantId={}", underwritingId, tenantId);
        ResponseEntity<UnderwritingDTO> response = underwritingApi.underwrite(underwritingId, request, tenantId);
        return response.getBody();
    }
}