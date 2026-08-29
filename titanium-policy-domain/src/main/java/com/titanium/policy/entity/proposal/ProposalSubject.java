package com.titanium.policy.entity.proposal;

import java.util.Map;

import com.titanium.metadata.enums.insurance.SubjectType;
import com.titanium.metadata.enums.underwriting.UnderwritingEnum;

/**
 * 投保意向单标的实体
 * <p>
 * 记录保险标的的简要信息，包括标的类型、标的简要信息和预估风险等级等
 * </p>
 */
public record ProposalSubject(
        /**
         * 标的ID，聚合内唯一
         */
        String subjectId,
        /**
         * 标的类型：车辆/房屋/人身/财产
         */
        SubjectType subjectType,
        /**
         * 标的简要信息：车牌号/房屋地址/姓名
         */
        String simpleInfo,
        /**
         * 预估风险等级
         */
        UnderwritingEnum.RiskLevel estimatedRiskLevel,
        /** 标的属性摘要（车辆/财产等物类标的用于后续投保段精化） */
        Map<String, Object> attributes
) {
    /** 兼容仅有简要信息的历史调用方。 */
    public ProposalSubject(String subjectId, SubjectType subjectType, String simpleInfo,
                           UnderwritingEnum.RiskLevel estimatedRiskLevel) {
        this(subjectId, subjectType, simpleInfo, estimatedRiskLevel, Map.of());
    }
    /**
     * 更新标的简要信息
     *
     * @param simpleInfo 新的标的简要信息
     * @return 更新后的标的实体
     */
    public ProposalSubject updateSubjectInfo(String simpleInfo) {
        return new ProposalSubject(subjectId, subjectType, simpleInfo, estimatedRiskLevel, attributes);
    }

    /**
     * 更新标的预估风险等级
     *
     * @param estimatedRiskLevel 新的预估风险等级
     * @return 更新后的标的实体
     */
    public ProposalSubject updateEstimatedRiskLevel(UnderwritingEnum.RiskLevel estimatedRiskLevel) {
        return new ProposalSubject(subjectId, subjectType, simpleInfo, estimatedRiskLevel, attributes);
    }
}
