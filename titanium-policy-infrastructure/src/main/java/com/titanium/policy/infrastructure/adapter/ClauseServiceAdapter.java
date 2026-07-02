package com.titanium.policy.infrastructure.adapter;

import java.util.List;

import org.springframework.stereotype.Component;

import com.titanium.clause.api.ClauseClient;
import com.titanium.clause.api.dto.ClauseDTO;
import com.titanium.policy.port.ClauseServicePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 条款服务适配器
 * <p>
 * {@link ClauseServicePort} 的基础设施实现，直接调用条款域 {@link ClauseClient}（Feign）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClauseServiceAdapter implements ClauseServicePort {

    private final ClauseClient clauseClient;

    @Override
    public Object getClauseById(String clauseId) {
        log.info("获取条款详情, clauseId={}", clauseId);
        return clauseClient.getClauseById(clauseId);
    }

    @Override
    public List<?> getClauses(String status, String clauseType) {
        log.info("获取条款列表, status={}, clauseType={}", status, clauseType);
        List<ClauseDTO> clauses = clauseClient.getClauses(status, clauseType);
        return clauses;
    }
}
