package com.titanium.policy.aggregate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

import com.titanium.metadata.enums.policy.InvestmentAccountStatus;
import com.titanium.policy.command.investment.AdjustUnitPriceCommand;
import com.titanium.policy.command.investment.AllocateUnitsCommand;
import com.titanium.policy.command.investment.CreateInvestmentAccountCommand;
import com.titanium.policy.command.investment.RedeemUnitsCommand;
import com.titanium.policy.event.investment.InvestmentAccountCreatedEvent;
import com.titanium.policy.event.investment.UnitPriceAdjustedEvent;
import com.titanium.policy.event.investment.UnitsAllocatedEvent;
import com.titanium.policy.event.investment.UnitsRedeemedEvent;
import com.titanium.policy.exception.InvestmentAccountRuleException;
import com.titanium.policy.valueobject.InvestmentAccountType;

import lombok.Getter;

/**
 * 投资账户聚合根（投连险/万能险专属）
 * <p>
 * 管理投连险保单关联的投资账户：持有单位数、单位净值、账户价值。支撑申购（保费买入单位）、
 * 赎回（部分领取/退保卖出单位）、每日估值（调整单位净值）。账户价值 = 持有单位数 × 当前单位净值。
 * </p>
 * <p>
 * 通过 {@code policyId} 以 ID 引用保单聚合，不直接持有 Policy 对象，遵循聚合间 ID 引用原则。
 * 状态机：ACTIVE ⇄ SUSPENDED → CLOSED。
 * </p>
 *
 * @author wei.sun
 * @since 2026/6/23
 */
@Getter
@Aggregate
public class InvestmentAccount {

    /** 单位数计算精度（4 位小数） */
    private static final int UNIT_SCALE = 4;
    /** 金额计算精度（2 位小数） */
    private static final int AMOUNT_SCALE = 2;

    @AggregateIdentifier
    private String                  accountId;
    /** 关联保单ID（ID 引用，不持有 Policy 对象） */
    private String                  policyId;
    /** 账户类型：UNIT_LINKED 投连 / UNIVERSAL 万能 */
    private InvestmentAccountType   accountType;
    /** 当前单位净值 */
    private BigDecimal              unitPrice;
    /** 持有单位数 */
    private BigDecimal              totalUnits;
    /** 币种 */
    private String                  currency;
    /** 管理费率（年化） */
    private BigDecimal              managementFeeRate;
    /** 账户状态 */
    private InvestmentAccountStatus status;
    /** 租户ID */
    private String                  tenantId;

    protected InvestmentAccount() {
    }

    /**
     * 创建投资账户
     */
    @CommandHandler
    public InvestmentAccount(CreateInvestmentAccountCommand cmd) {
        if (cmd.initialUnitPrice() == null || cmd.initialUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvestmentAccountRuleException("INVEST_UNIT_PRICE_INVALID", "初始单位净值必须大于0");
        }
        AggregateLifecycle.apply(new InvestmentAccountCreatedEvent(
                cmd.accountId(), cmd.policyId(), cmd.accountType(), cmd.initialUnitPrice(),
                cmd.currency(), cmd.managementFeeRate(), InvestmentAccountStatus.ACTIVE.name(),
                LocalDateTime.now(), cmd.tenantId()));
    }

    /**
     * 申购：保费投入，按当前单位净值买入单位数
     */
    @CommandHandler
    public void handle(AllocateUnitsCommand cmd) {
        ensureActive("申购");
        if (cmd.allocationAmount() == null || cmd.allocationAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvestmentAccountRuleException("INVEST_ALLOCATION_INVALID", "申购金额必须大于0");
        }
        BigDecimal allocatedUnits = cmd.allocationAmount().divide(this.unitPrice, UNIT_SCALE, RoundingMode.HALF_UP);
        BigDecimal newTotal = this.totalUnits.add(allocatedUnits);
        AggregateLifecycle.apply(new UnitsAllocatedEvent(
                this.accountId, cmd.allocationAmount(), this.unitPrice, allocatedUnits, newTotal,
                cmd.source(), LocalDateTime.now(), cmd.operatorId()));
    }

    /**
     * 赎回：部分领取/退保，按当前单位净值卖出单位数
     */
    @CommandHandler
    public void handle(RedeemUnitsCommand cmd) {
        ensureActive("赎回");
        if (cmd.redeemUnits() == null || cmd.redeemUnits().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvestmentAccountRuleException("INVEST_REDEEM_INVALID", "赎回单位数必须大于0");
        }
        if (cmd.redeemUnits().compareTo(this.totalUnits) > 0) {
            throw new InvestmentAccountRuleException("INVEST_REDEEM_EXCEED",
                    String.format("赎回单位数 %s 超过持有单位数 %s", cmd.redeemUnits(), this.totalUnits));
        }
        BigDecimal redeemAmount = cmd.redeemUnits().multiply(this.unitPrice)
                .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        BigDecimal remaining = this.totalUnits.subtract(cmd.redeemUnits());
        AggregateLifecycle.apply(new UnitsRedeemedEvent(
                this.accountId, cmd.redeemUnits(), this.unitPrice, redeemAmount, remaining,
                cmd.reason(), LocalDateTime.now(), cmd.operatorId()));
    }

    /**
     * 调整单位净值：每日估值
     */
    @CommandHandler
    public void handle(AdjustUnitPriceCommand cmd) {
        if (this.status == InvestmentAccountStatus.CLOSED) {
            throw new InvestmentAccountRuleException("INVEST_ACCOUNT_CLOSED", "已关闭账户不可调整净值");
        }
        if (cmd.newUnitPrice() == null || cmd.newUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvestmentAccountRuleException("INVEST_UNIT_PRICE_INVALID", "单位净值必须大于0");
        }
        BigDecimal accountValue = this.totalUnits.multiply(cmd.newUnitPrice())
                .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        AggregateLifecycle.apply(new UnitPriceAdjustedEvent(
                this.accountId, this.unitPrice, cmd.newUnitPrice(), this.totalUnits, accountValue,
                cmd.valuationDate(), LocalDateTime.now()));
    }

    /**
     * 当前账户价值 = 持有单位数 × 单位净值
     *
     * @return 账户价值
     */
    public BigDecimal accountValue() {
        return this.totalUnits.multiply(this.unitPrice).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private void ensureActive(String operation) {
        if (this.status != InvestmentAccountStatus.ACTIVE) {
            throw new InvestmentAccountRuleException("INVEST_ACCOUNT_NOT_ACTIVE",
                    String.format("账户非活跃状态(%s)，禁止%s", this.status, operation));
        }
    }

    @EventSourcingHandler
    public void on(InvestmentAccountCreatedEvent e) {
        this.accountId = e.accountId();
        this.policyId = e.policyId();
        this.accountType = e.accountType();
        this.unitPrice = e.initialUnitPrice();
        this.totalUnits = BigDecimal.ZERO;
        this.currency = e.currency();
        this.managementFeeRate = e.managementFeeRate();
        this.status = InvestmentAccountStatus.valueOf(e.status());
        this.tenantId = e.tenantId();
    }

    @EventSourcingHandler
    public void on(UnitsAllocatedEvent e) {
        this.totalUnits = e.totalUnits();
    }

    @EventSourcingHandler
    public void on(UnitsRedeemedEvent e) {
        this.totalUnits = e.remainingUnits();
    }

    @EventSourcingHandler
    public void on(UnitPriceAdjustedEvent e) {
        this.unitPrice = e.newUnitPrice();
    }
}
