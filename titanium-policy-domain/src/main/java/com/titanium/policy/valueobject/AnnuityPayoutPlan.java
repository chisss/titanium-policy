package com.titanium.policy.valueobject;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.titanium.metadata.valueobject.Money;
import com.titanium.policy.common.enums.AnnuityPayoutFrequency;
import com.titanium.policy.common.enums.AnnuityPayoutStatus;

/**
 * 年金给付计划值对象
 * <p>
 * 承载年金保险（{@code InsuranceProductType.ANNUITY}）进入给付期后的给付安排：给付起始日、
 * 给付频率、每期给付金额、总给付期数与已给付期数。年金给付以「被保险人生存」为条件，
 * 由系统按频率主动到期触发，逐期推进而<b>不终止保单</b>——这是与身故给付的本质区别。
 * </p>
 * <p>
 * 充血不可变值对象：{@link #payNextInstallment(LocalDateTime)} 返回推进一期后的新实例，
 * 原实例不变，契合事件溯源「事件 → 新状态」的重建语义。总期数为 {@code null} 表示终身年金
 * （以生存为唯一给付条件，无固定期数上限）。
 * </p>
 *
 * @param startDate       给付起始日
 * @param frequency       给付频率
 * @param installmentAmount 每期给付金额
 * @param totalInstallments 总给付期数（{@code null} 表示终身年金，无固定期数）
 * @param paidInstallments  已给付期数
 * @param nextPayoutDate    下一期给付日
 * @param status          给付计划状态
 */
public record AnnuityPayoutPlan(LocalDateTime startDate, AnnuityPayoutFrequency frequency, Money installmentAmount,
                                Integer totalInstallments, int paidInstallments, LocalDateTime nextPayoutDate,
                                AnnuityPayoutStatus status) {

    public AnnuityPayoutPlan {
        if (startDate == null) {
            throw new IllegalArgumentException("年金给付起始日不能为空");
        }
        if (frequency == null) {
            throw new IllegalArgumentException("年金给付频率不能为空");
        }
        if (installmentAmount == null || installmentAmount.isZero()) {
            throw new IllegalArgumentException("年金每期给付金额必须大于零");
        }
        if (totalInstallments != null && totalInstallments <= 0) {
            throw new IllegalArgumentException("年金总给付期数必须大于零（终身年金请传 null）");
        }
        if (paidInstallments < 0) {
            throw new IllegalArgumentException("年金已给付期数不能为负");
        }
    }

    /**
     * 创建初始给付计划（给付期启动时使用），首期给付日为给付起始日，状态置为给付中。
     *
     * @param startDate         给付起始日
     * @param frequency         给付频率
     * @param installmentAmount 每期给付金额
     * @param totalInstallments 总给付期数（{@code null} 表示终身年金）
     * @return 初始年金给付计划
     */
    public static AnnuityPayoutPlan start(LocalDateTime startDate, AnnuityPayoutFrequency frequency,
                                          Money installmentAmount, Integer totalInstallments) {
        return new AnnuityPayoutPlan(startDate, frequency, installmentAmount, totalInstallments, 0, startDate,
                AnnuityPayoutStatus.PAYING);
    }

    /**
     * 是否为终身年金（无固定给付期数，以生存为唯一给付条件）。
     *
     * @return 终身年金返回 {@code true}
     */
    @JsonIgnore
    public boolean isWholeLife() {
        return this.totalInstallments == null;
    }

    /**
     * 给付计划是否已完成（定期年金给付满总期数）。终身年金永不因期数完成。
     *
     * @return 已完成返回 {@code true}
     */
    @JsonIgnore
    public boolean isCompleted() {
        return this.status == AnnuityPayoutStatus.COMPLETED
                || (!isWholeLife() && this.paidInstallments >= this.totalInstallments);
    }

    /**
     * 推进一期给付，返回给付后的新实例。
     * <p>
     * 已给付期数 +1，下一给付日按频率顺延；若定期年金给满总期数则状态转为已完成。
     * </p>
     *
     * @param payoutTime 本期实际给付时间（用于推算下一给付日基准）
     * @return 推进一期后的新给付计划
     * @throws IllegalStateException 计划非给付中或已完成时推进
     */
    public AnnuityPayoutPlan payNextInstallment(LocalDateTime payoutTime) {
        if (this.status != AnnuityPayoutStatus.PAYING) {
            throw new IllegalStateException("仅给付中的年金计划可推进给付，当前状态：" + this.status);
        }
        if (isCompleted()) {
            throw new IllegalStateException("年金给付计划已完成，不可再给付");
        }
        int newPaid = this.paidInstallments + 1;
        boolean nowCompleted = !isWholeLife() && newPaid >= this.totalInstallments;
        AnnuityPayoutStatus newStatus = nowCompleted ? AnnuityPayoutStatus.COMPLETED : AnnuityPayoutStatus.PAYING;
        LocalDateTime newNext = nowCompleted ? this.nextPayoutDate : this.frequency.nextPayoutDate(payoutTime);
        return new AnnuityPayoutPlan(this.startDate, this.frequency, this.installmentAmount, this.totalInstallments,
                newPaid, newNext, newStatus);
    }

    /**
     * 中止给付计划，返回状态置为 {@code STOPPED} 的新实例。
     * <p>
     * 用于保单因身故给付/退保等外部原因终止时，联动停止年金给付（被保险人身故后不再有生存年金）。
     * 已完成/已中止的计划再次中止为幂等，直接返回自身。
     * </p>
     *
     * @return 中止后的年金给付计划
     */
    public AnnuityPayoutPlan stop() {
        if (this.status != AnnuityPayoutStatus.PAYING) {
            return this;
        }
        return new AnnuityPayoutPlan(this.startDate, this.frequency, this.installmentAmount, this.totalInstallments,
                this.paidInstallments, this.nextPayoutDate, AnnuityPayoutStatus.STOPPED);
    }
}
