package com.titanium.policy.query.result;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.titanium.metadata.enums.CurrencyEnum;
import com.titanium.metadata.enums.policy.PolicyEnum;
import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.PeriodUnit;
import com.titanium.policy.common.enums.PolicyItemType;

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


    // ==================== 一单多险 / 收费 / 渠道 / 期间（本期新增） ====================

    /** 关联意向单ID（三步出单来源，支撑三级贯通溯源） */
    private String                      proposalId;

    /** 关联核保单ID（承保依据溯源） */
    private String                      underwritingId;

    /** 营销包ID（弱引用 marketing 域） */
    private String                      marketPackageId;

    /** 主险产品ID（险种真相在险种段上，此为高频查询冗余） */
    private String                      productId;

    /** 保单总保费（= Σ 计入段的保费，拒保段已剔除） */
    private BigDecimal                  totalPremium;

    /** 险种段数量（单险种为 1，一单多险 > 1） */
    private Integer                     lineCount;

    /** 等待期届满日（此前疾病类责任不赔） */
    private LocalDateTime               waitingPeriodEndDate;

    /** 犹豫期届满日（此前可无条件退保） */
    private LocalDateTime               hesitationPeriodEndDate;

    /** 收费方式码（OFFLINE/ONLINE/FREE/PAY_AFTER_USE/WITHHOLD） */
    private String                      collectionMode;

    /** 收讫状态码 */
    private String                      collectionStatus;

    /** 已收保费金额 */
    private BigDecimal                  collectedAmount;

    /** 渠道ID（指向 channel 域） */
    private String                      channelId;

    /** 销售渠道大类码 */
    private String                      salesChannel;

    /** 代理人/业务员ID */
    private String                      agentId;

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
