package com.titanium.policy.application.service;

import com.titanium.clause.api.dto.ClauseDTO;
import com.titanium.policy.service.ClauseServicePort;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 条款服务适配器
 * 实现ClauseServicePort接口，适配ClauseService的调用
 */
@Slf4j
@Service
public class ClauseServiceAdapter implements ClauseServicePort {

    @Resource
    private ClauseService clauseService;

    @Override
    public Object getClauseById(String clauseId) {
        return clauseService.getClauseById(clauseId);
    }

    @Override
    public List<?> getClauses(String status, String clauseType) {
        return clauseService.getClauses(status, clauseType);
    }
}