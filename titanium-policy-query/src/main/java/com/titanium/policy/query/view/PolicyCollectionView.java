package com.titanium.policy.query.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.common.jpa.BaseView;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 保单收费信息读模型实体（CQRS 读侧）
 * <p>
 * 回答「通过什么方式收了多少钱」：收费方式、关联账单与支付单、应收与实收金额、收讫状态与时间。
 * 一保单一行，由 {@code PolicyCollectionProjectionEventHandler} 从 {@code PolicyCreatedEvent}
 * 的收费信息与 {@code PremiumCollectedEvent} 投影维护。
 * </p>
 */
@Entity
@Table(name = "t_policy_collection", indexes = {
        @Index(name = "idx_policy_collection_bill", columnList = "bill_id"),
        @Index(name = "idx_policy_collection_payment", columnList = "payment_order_id"),
        @Index(name = "idx_policy_collection_tenant", columnList = "tenant_id")
})
@Getter
@Setter
public class PolicyCollectionView extends BaseView {

    /** 主键（policyId 派生，一保单一行） */
    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String        id;

    /** 保单ID */
    @Column(name = "policy_id", nullable = false, length = 36)
    private String        policyId;

    /** 收费方式码（OFFLINE/ONLINE/FREE/PAY_AFTER_USE/WITHHOLD） */
    @Column(name = "collection_mode", length = 32)
    private String        collectionMode;

    /** 账单ID（billing 域） */
    @Column(name = "bill_id", length = 64)
    private String        billId;

    /** 支付单ID（payment 域；线下与免支付无支付单） */
    @Column(name = "payment_order_id", length = 64)
    private String        paymentOrderId;

    /** 应收金额 */
    @Column(name = "payable_amount", precision = 18, scale = 2)
    private BigDecimal    payableAmount;

    /** 已收金额 */
    @Column(name = "collected_amount", precision = 18, scale = 2)
    private BigDecimal    collectedAmount;

    /** 币种（ISO 4217） */
    @Column(name = "currency", length = 8)
    private String        currency;

    /** 收讫状态码（UNCOLLECTED/PARTIALLY_COLLECTED/COLLECTED/DEFERRED/OVERDUE） */
    @Column(name = "collection_status", length = 32)
    private String        collectionStatus;

    /** 收讫时间（未收讫为空） */
    @Column(name = "collected_time")
    private LocalDateTime collectedTime;
}
