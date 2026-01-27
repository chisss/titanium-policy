package com.titanium.policy.entity;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.policy.valueobject.Amount;

/**
 * 投保险种实体类
 * <p>
 * 表示一个具体的保险产品，包含产品ID、产品代码、产品名称、保障责任、保额、保险期限、保费等信息
 * </p>
 * 
 * @param productId 保险产品ID，唯一标识一个保险产品
 * @param productCode 保险产品代码，用于外部系统引用
 * @param productName 保险产品名称，用于展示给用户
 * @param sumInsured 保额，保险的基本保障金额
 * @param insurancePeriodStart 保险期限开始时间，定义保险生效时间
 * @param insurancePeriodEnd 保险期限结束时间，定义保险失效时间
 * @param premium 保费，用户支付的保险费用
 * @param coverages 保障责任列表，包含保险产品的具体保障内容
 */
public record InsuranceProduct(String productId, String productCode, String productName, Amount sumInsured,
                               LocalDateTime insurancePeriodStart, LocalDateTime insurancePeriodEnd, Amount premium,
                               List<Coverage> coverages) {

    /**
     * 关联保障责任
     *
     * @param coverage 保障责任
     * @return 更新后的投保险种
     */
    public InsuranceProduct addCoverage(Coverage coverage) {

        this.coverages.add(coverage);
        return new InsuranceProduct(this.productId, this.productCode, this.productName, this.sumInsured,
                this.insurancePeriodStart, this.insurancePeriodEnd, this.premium, this.coverages);
    }

    /**
     * 绑定保险标的
     * <p>
     * 关联保险标的，维护标的与险种的关系
     * </p>
     *
     * @param subjectId 标的ID
     * @return 更新后的投保险种
     */
    public InsuranceProduct bindSubject(String subjectId) {
        // 这里应该绑定标的到险种
        // 暂时返回当前险种
        return new InsuranceProduct(this.productId, this.productCode, this.productName, this.sumInsured,
                this.insurancePeriodStart, this.insurancePeriodEnd, this.premium, this.coverages);
    }
}
