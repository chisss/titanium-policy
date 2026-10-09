package com.titanium.policy.api.request.maintenance;

import java.math.BigDecimal;

import com.titanium.metadata.enums.pricing.ChargeDirection;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 金额型保全项随请求递达保单域的收付差额事实。
 * <p>
 * 与「结构化字段变更」「合同状态动作」<b>并列</b>的第三种生效载荷。追加/减额缴费、追加投资、部分领取
 * 这类纯金额类保全项既无字段目录入口、也不改合同状态，变更的实质就是这笔差额本身。
 * </p>
 * <p>
 * 🔴 金额由保全域在生效环节从<b>已登记的资金事实</b>（Billing 入账差额优先、Product 报价差额回落）解析，
 * 本契约只负责搬运，<b>不接受调用方自报金额</b>；保单域亦不重新计价。
 * </p>
 *
 * @param itemCode  所属保全项编码（保单域据以推导批改类型，不可缺省）
 * @param amount    收付金额（恒为正的绝对值，方向由 direction 表达）
 * @param direction 收付方向（仅 DEBIT/CREDIT，NONE 无意义）
 * @param currency  币种（三字母代码）
 * @param sourceRef 差额来源凭证（Billing 入账凭证号或 Product 报价号）
 */
@Schema(description = "保全金额事实")
public record PolicyMaintenanceAmountRequest(
        @Schema(description = "所属保全项编码", example = "ADDITIONAL_PAYMENT")
        @NotBlank @Size(max = 64) String itemCode,

        @Schema(description = "收付金额（正数）", example = "1200.00")
        @NotNull @Positive BigDecimal amount,

        @Schema(description = "收付方向")
        @NotNull ChargeDirection direction,

        @Schema(description = "币种（三字母代码）", example = "CNY")
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,

        @Schema(description = "差额来源凭证", example = "posting-20260928-001")
        @NotBlank @Size(max = 128) String sourceRef) {
}
