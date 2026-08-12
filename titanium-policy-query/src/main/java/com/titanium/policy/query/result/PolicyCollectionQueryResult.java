package com.titanium.policy.query.result;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 保单收费信息查询结果（读侧对外契约）
 * <p>
 * 回答「通过什么方式收了多少钱」：收费方式、关联账单与支付单、应收与实收、收讫状态与时间。
 * </p>
 */
@Data
public class PolicyCollectionQueryResult {

    /** 保单ID */
    private String        policyId;

    /** 收费方式码（OFFLINE/ONLINE/FREE/PAY_AFTER_USE/WITHHOLD） */
    private String        collectionMode;

    /** 账单ID（billing 域） */
    private String        billId;

    /** 支付单ID（payment 域） */
    private String        paymentOrderId;

    /** 应收金额 */
    private BigDecimal    payableAmount;

    /** 已收金额 */
    private BigDecimal    collectedAmount;

    /** 币种 */
    private String        currency;

    /** 收讫状态码 */
    private String        collectionStatus;

    /** 收讫时间 */
    private LocalDateTime collectedTime;
}
