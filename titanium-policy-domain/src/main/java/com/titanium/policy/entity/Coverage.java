package com.titanium.policy.entity;

import com.titanium.metadata.valueobject.Money;

/**
 * 保障责任实体
 * <p>
 * 记录保险责任的详细信息，包括责任编码、责任名称、责任保额、赔付条件等
 * </p>
 */
public record Coverage(
                       /**
                        * 责任ID，聚合内唯一
                        */
                       String coverageId,
                       /**
                        * 责任编码
                        */
                       String coverageCode,
                       /**
                        * 责任名称
                        */
                       String coverageName,
                       /**
                        * 责任保额
                        */
                       Money coverageSumInsured,
                       /**
                        * 赔付条件
                        */
                       String indemnityCondition,
                       /**
                        * 免责条款
                        */
                       String exclusionClause,
                       /**
                        * 赔付比例
                        */
                       double indemnityRatio) {
    /**
     * 校验赔付条件合法性
     * <p>
     * 校验赔付条件是否符合产品域规则
     * </p>
     *
     * @return 校验结果，true表示校验通过
     */
    public boolean verifyIndemnityCondition() {
        // 这里应该调用产品域的规则校验
        // 暂时返回true
        return true;
    }
}
