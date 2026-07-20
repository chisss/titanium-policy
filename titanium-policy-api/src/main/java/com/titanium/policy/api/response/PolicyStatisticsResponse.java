package com.titanium.policy.api.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 保单统计远程响应契约（Feign 出参）
 * <p>
 * 面向管理后台等跨服务消费者的对外传输契约，承载保单维度聚合统计：有效保单数、今日新增数、
 * 保单总数及按险种一级分类的保单数分布。由 web 层经 MapStruct 从读侧 {@code PolicyStatisticsResult}
 * 转换而来，作为稳定协议隔离读模型内部结构。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PolicyStatisticsResponse {

    /** 有效保单数（状态为 EFFECTIVE） */
    private long activePolicyCount;

    /** 今日新增保单数（create_time 落在当天） */
    private long todayPolicyCount;

    /** 保单总数 */
    private long totalPolicyCount;

    /** 按险种一级分类的保单数分布 */
    private List<CategoryDistribution> insuranceCategoryDistribution;

    /**
     * 险种分类分布项（看板 {name, value} 结构）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryDistribution {

        /** 分类名称（险种一级分类中文名，如「人身保险」） */
        private String name;

        /** 该分类下的保单数 */
        private long value;
    }
}
