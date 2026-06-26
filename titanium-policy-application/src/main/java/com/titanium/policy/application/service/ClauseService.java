package com.titanium.policy.application.service;

import com.titanium.clause.api.ClauseClient;
import com.titanium.clause.api.dto.ClauseDTO;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 条款服务客户端
 * 用于调用条款系统的API
 */
@Slf4j
@Service
public class ClauseService {

    @Resource
    private ClauseClient clauseClient;

    /**
     * 根据条款ID获取条款详情
     */
    public ClauseDTO getClauseById(String clauseId) {
        log.info("获取条款详情, clauseId={}", clauseId);
        return clauseClient.getClauseById(clauseId);
    }

    /**
     * 获取条款列表
     */
    public List<ClauseDTO> getClauses(String status, String clauseType) {
        log.info("获取条款列表, status={}, clauseType={}", status, clauseType);
        return clauseClient.getClauses(status, clauseType);
    }
}