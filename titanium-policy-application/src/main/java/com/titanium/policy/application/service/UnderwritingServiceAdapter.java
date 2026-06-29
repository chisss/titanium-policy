package com.titanium.policy.application.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.titanium.policy.service.UnderwritingServicePort;
import com.titanium.underwriting.api.request.CreateUnderwritingRequest;
import com.titanium.underwriting.api.request.UnderwriteRequest;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

/**
 * 核保服务适配器
 * 实现UnderwritingServicePort接口，适配UnderwritingService的调用
 */
@Slf4j
@Service
public class UnderwritingServiceAdapter implements UnderwritingServicePort {

    @Resource
    private UnderwritingService underwritingService;

    @Override
    public Object createUnderwriting(Object request, String tenantId) {
        if (request instanceof CreateUnderwritingRequest createRequest) {
            return underwritingService.createUnderwriting(createRequest, tenantId);
        } else {
            throw new IllegalArgumentException("Invalid request type for createUnderwriting");
        }
    }

    @Override
    public Object getUnderwritingById(String underwritingId, String tenantId) {
        return underwritingService.getUnderwritingById(underwritingId, tenantId);
    }

    @Override
    public List<?> getUnderwritingByPolicyId(String policyId, String tenantId) {
        return underwritingService.getUnderwritingByPolicyId(policyId, tenantId);
    }

    @Override
    public Object underwrite(String underwritingId, Object request, String tenantId) {
        if (request instanceof UnderwriteRequest underwriteRequest) {
            return underwritingService.underwrite(underwritingId, underwriteRequest, tenantId);
        } else {
            throw new IllegalArgumentException("Invalid request type for underwrite");
        }
    }
}
