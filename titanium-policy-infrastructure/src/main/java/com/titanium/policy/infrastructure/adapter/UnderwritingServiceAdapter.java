package com.titanium.policy.infrastructure.adapter;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.titanium.policy.port.UnderwritingServicePort;
import com.titanium.underwriting.api.UnderwritingApi;
import com.titanium.underwriting.api.dto.UnderwritingDTO;
import com.titanium.underwriting.api.request.CreateUnderwritingRequest;
import com.titanium.underwriting.api.request.UnderwriteRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 核保服务适配器
 * <p>
 * {@link UnderwritingServicePort} 的基础设施实现，直接调用核保域 {@link UnderwritingApi}（Feign）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnderwritingServiceAdapter implements UnderwritingServicePort {

    private final UnderwritingApi underwritingApi;

    @Override
    public Object createUnderwriting(Object request, String tenantId) {
        if (request instanceof CreateUnderwritingRequest createRequest) {
            log.info("创建核保, tenantId={}", tenantId);
            ResponseEntity<UnderwritingDTO> response = underwritingApi.createUnderwriting(createRequest, tenantId);
            return response.getBody();
        }
        throw new IllegalArgumentException("Invalid request type for createUnderwriting");
    }

    @Override
    public Object getUnderwritingById(String underwritingId, String tenantId) {
        log.info("获取核保详情, underwritingId={}, tenantId={}", underwritingId, tenantId);
        ResponseEntity<UnderwritingDTO> response = underwritingApi.getUnderwritingById(underwritingId, tenantId);
        return response.getBody();
    }

    @Override
    public List<?> getUnderwritingByPolicyId(String policyId, String tenantId) {
        log.info("根据保单ID获取核保, policyId={}, tenantId={}", policyId, tenantId);
        ResponseEntity<List<UnderwritingDTO>> response = underwritingApi.getUnderwritingByPolicyId(policyId, tenantId);
        return response.getBody();
    }

    @Override
    public Object underwrite(String underwritingId, Object request, String tenantId) {
        if (request instanceof UnderwriteRequest underwriteRequest) {
            log.info("执行核保, underwritingId={}, tenantId={}", underwritingId, tenantId);
            ResponseEntity<UnderwritingDTO> response = underwritingApi.underwrite(underwritingId, underwriteRequest,
                    tenantId);
            return response.getBody();
        }
        throw new IllegalArgumentException("Invalid request type for underwrite");
    }
}
