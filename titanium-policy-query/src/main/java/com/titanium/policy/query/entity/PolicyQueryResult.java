package com.titanium.policy.query.entity;

import java.time.LocalDateTime;
import java.util.List;

import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.PeriodUnit;
import com.titanium.policy.valueobject.PolicyItemType;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 保单查询结果实体
 * <p>
 * 用于封装复杂查询的结果数据
 * </p>
 */
@Data
@NoArgsConstructor
public class PolicyQueryResult {
    /**
     * 保单ID
     */
    private String                      policyId;

    /**
     * 保单号
     */
    private String                      policyNo;

    /**
     * 投保单ID
     */
    private String                      applicationId;

    /**
     * 保单形态
     */
    private PolicyForm                  policyForm;

    /**
     * 投保人ID
     */
    private String                      policyHolderId;

    /**
     * 投保人姓名
     */
    private String                      policyHolderName;

    /**
     * 被保险人ID
     */
    private String                      insuredId;

    /**
     * 被保险人姓名
     */
    private String                      insuredName;

    /**
     * 险种编码
     */
    private String                      productCode;

    /**
     * 险种名称
     */
    private String                      productName;

    /**
     * 保额
     */
    private Double                      sumInsured;

    /**
     * 保费
     */
    private Double                      premium;

    /**
     * 币种
     */
    private CurrencyEnum                currency;

    /**
     * 生效日期
     */
    private LocalDateTime               effectiveDate;

    /**
     * 终止日期
     */
    private LocalDateTime               expiryDate;

    /**
     * 保单状态
     */
    private PolicyEnum.PolicyStatus     status;

    /**
     * 创建时间
     */
    private LocalDateTime               createTime;

    /**
     * 更新时间
     */
    private LocalDateTime               updateTime;

    /**
     * 租户ID
     */
    private String                      tenantId;

    /**
     * 保单项目列表
     */
    private List<PolicyItemQueryResult> policyItems;

    /**
     * 保单项目查询结果
     */
    @Data
    @NoArgsConstructor
    public static class PolicyItemQueryResult {
        /**
         * 项目ID
         */
        private String  itemId;

        /**
         * 项目类型
         */
        private PolicyItemType itemType;

        /**
         * 项目名称
         */
        private String  itemName;

        /**
         * 保额
         */
        private Double  sumInsured;

        /**
         * 保费
         */
        private Double  premium;

        /**
         * 保险期限
         */
        private Integer insurancePeriod;

        /**
         * 保险期限单位
         */
        private PeriodUnit periodUnit;
    }
}
