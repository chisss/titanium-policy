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
 * 年金给付计划读模型实体（CQRS 读侧）
 * <p>
 * 由 {@code AnnuityPayoutStartedEvent}/{@code AnnuityBenefitPaidEvent} 投影维护，映射
 * {@code t_annuity_payout_plan}，对外展示年金保单给付期的计划状态、已给付/总期数、下一给付日等。
 * 年金给付以被保险人生存为条件、逐期推进而不终止保单，故本读模型独立于保单主视图。
 * </p>
 * <p>
 * 年金计划与保单 1:1，故以保单ID作主键（投影时 {@code id = policyId}），保证幂等 upsert。
 * 继承 {@link BaseView}，复用租户ID、投影时间、乐观锁版本等读模型公共字段。
 * </p>
 */
@Entity
@Table(name = "t_annuity_payout_plan", indexes = {
        @Index(name = "idx_annuity_payout_policy", columnList = "policy_id, tenant_id"),
        @Index(name = "idx_annuity_payout_tenant", columnList = "tenant_id")
})
@Getter
@Setter
public class AnnuityPayoutPlanView extends BaseView {

    /** 主键：与保单 1:1，投影时置为保单ID */
    @Id
    @Column(name = "id", nullable = false, length = 32)
    private String        id;

    /** 保单ID */
    @Column(name = "policy_id", nullable = false, length = 36)
    private String        policyId;

    /** 给付起始日 */
    @Column(name = "start_date")
    private LocalDateTime startDate;

    /** 给付频率码 */
    @Column(name = "frequency", length = 32)
    private String        frequency;

    /** 每期给付金额 */
    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal    amount;

    /** 币种(ISO4217) */
    @Column(name = "currency", length = 3)
    private String        currency;

    /** 总给付期数（null 表示终身年金） */
    @Column(name = "total_installments")
    private Integer       totalInstallments;

    /** 已给付期数 */
    @Column(name = "paid_installments")
    private Integer       paidInstallments;

    /** 下一给付日 */
    @Column(name = "next_payout_date")
    private LocalDateTime nextPayoutDate;

    /** 给付状态码 */
    @Column(name = "payout_status", length = 32)
    private String        payoutStatus;
}
