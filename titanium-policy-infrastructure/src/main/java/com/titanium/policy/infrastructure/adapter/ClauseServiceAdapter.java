package com.titanium.policy.infrastructure.adapter;

import java.util.List;

import org.springframework.stereotype.Component;

import com.titanium.clause.api.ClauseApi;
import com.titanium.clause.api.dto.ClauseDTO;
import com.titanium.policy.port.ClauseServicePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 条款服务适配器
 * <p>
 * {@link ClauseServicePort} 的基础设施实现，直接调用条款域 {@link ClauseApi}（Feign）。
 * 条款契约方法透传 {@code X-Tenant-Id}，本适配器当前无租户上下文，暂以 {@code default} 兜底
 * （待 Port 契约补齐 tenantId 参数后透传真实租户）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClauseServiceAdapter implements ClauseServicePort {

    private static final String DEFAULT_TENANT = "default";

    private final ClauseApi clauseApi;

    @Override
    public Object getClauseById(String clauseId) {
        log.info("获取条款详情, clauseId={}", clauseId);
        return clauseApi.getClauseById(clauseId, DEFAULT_TENANT);
    }

    @Override
    public List<?> getClauses(String status, String clauseType) {
        log.info("获取条款列表, status={}, clauseType={}", status, clauseType);
        List<ClauseDTO> clauses = clauseApi.getClauses(status, clauseType, DEFAULT_TENANT);
        return clauses;
    }
}
