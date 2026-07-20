package com.titanium.policy.query.result;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 保单统计查询结果（管理后台看板聚合用）
 * <p>
 * 承载保单维度的聚合统计：有效保单数、今日新增数、保单总数，以及按险种一级分类的保单数分布。
 * 全部数据来源于读模型表 {@code t_policy_view}，属最终一致视图，仅供展示，不用于业务强一致决策。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PolicyStatisticsResult {

    /** 有效保单数（状态为 EFFECTIVE） */
    private long                     activePolicyCount;

    /** 今日新增保单数（create_time 落在当天） */
    private long                     todayPolicyCount;

    /** 保单总数 */
    private long                     totalPolicyCount;

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
        private long   value;
    }
}
