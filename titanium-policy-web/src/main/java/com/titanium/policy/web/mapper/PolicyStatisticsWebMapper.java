package com.titanium.policy.web.mapper;

import org.mapstruct.Mapper;

import com.titanium.policy.api.response.PolicyStatisticsResponse;
import com.titanium.policy.query.result.PolicyStatisticsResult;

/**
 * 保单统计 Web 层对象映射器（MapStruct）
 * <p>
 * 将读侧统计结果 {@link PolicyStatisticsResult} 声明式映射为对外远程契约 {@link PolicyStatisticsResponse}。
 * 顶层与嵌套 {@code CategoryDistribution} 字段同名同类型，由 MapStruct 自动映射，无需手工 set。
 * </p>
 */
@Mapper(componentModel = "spring")
public interface PolicyStatisticsWebMapper {

    /** 读侧统计结果 → 对外统计响应（含嵌套险种分布，字段同名自动映射） */
    PolicyStatisticsResponse toResponse(PolicyStatisticsResult result);

    /** 嵌套：险种分类分布项转换（字段同名自动映射） */
    PolicyStatisticsResponse.CategoryDistribution toCategoryDistribution(
            PolicyStatisticsResult.CategoryDistribution distribution);
}
