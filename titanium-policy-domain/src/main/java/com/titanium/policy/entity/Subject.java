package com.titanium.policy.entity;

import com.titanium.metadata.enums.insurance.SubjectType;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;

/**
 * 保险标的实体
 * <p>
 * 记录保险标的的详细信息，包括标的类型、详细信息、最终风险等级等
 * </p>
 */
public record Subject(
                      /*
                       * 标的ID，聚合内唯一
                       */
                      String subjectId,
                      /*
                       * 标的类型
                       */
                      SubjectType subjectType,
                      /*
                       * 标的详细信息
                       */
                      String detailInfo,
                      /*
                       * 最终风险等级
                       */
                      UnderwritingEnum.RiskLevel riskLevel,
                      /*
                       * 标的合格状态
                       */
                      boolean qualifiedStatus) {
    /**
     * 更新标的状态
     * <p>
     * 标的状态变更时，同步更新险种责任
     * </p>
     *
     * @param qualifiedStatus 新的合格状态
     * @return 更新后的标的实体
     */
    public Subject updateSubjectStatus(Boolean qualifiedStatus) {
        return new Subject(subjectId, subjectType, detailInfo, riskLevel, qualifiedStatus);
    }

    /**
     * 更新标的风险等级
     * <p>
     * 更新标的的最终风险等级
     * </p>
     *
     * @param riskLevel 新的风险等级
     * @return 更新后的标的实体
     */
    public Subject updateRiskLevel(UnderwritingEnum.RiskLevel riskLevel) {
        return new Subject(subjectId, subjectType, detailInfo, riskLevel, qualifiedStatus);
    }
}
