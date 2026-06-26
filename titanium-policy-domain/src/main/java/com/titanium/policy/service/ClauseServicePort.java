package com.titanium.policy.service;

import java.util.List;

/**
 * 条款服务端口
 * 定义条款服务的接口，由应用层实现
 */
public interface ClauseServicePort {
    /**
     * 根据条款ID获取条款详情
     */
    Object getClauseById(String clauseId);

    /**
     * 获取条款列表
     */
    List<?> getClauses(String status, String clauseType);
}