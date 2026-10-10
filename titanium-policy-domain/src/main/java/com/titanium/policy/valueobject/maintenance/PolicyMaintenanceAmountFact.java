package com.titanium.policy.valueobject.maintenance;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;

import com.titanium.metadata.enums.pricing.ChargeDirection;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.policy.exception.PolicyBusinessRuleException;

/**
 * Policy 聚合据以落批单财务留痕的收付差额事实（金额 + 方向 + 币种 + 来源凭证 + 所属保全项）。
 * <p>
 * 与 {@link PolicyMaintenanceFieldChange}（字段变更）并列的另一种保成效力载荷。纯金额类保全项
 * （追加/减额缴费、追加投资、部分领取）不含任何字段变更，其合同效力<b>就是这笔收付本身</b>——
 * 若只落一条无金额的批单，事后无从回答「这次批改动了多少钱」。
 * </p>
 * <p>
 * 🔴 <b>构造期不变量是「拒绝空交易」的最后一道闸口，不是调用方约定</b>：金额必须严格大于零、
 * 方向必须明确且非 {@link ChargeDirection#NONE}、币种为三字母代码、来源凭证与保全项编码非空。
 * 保全域已在生效环节做过同形校验，此处重做是<b>纵深防御</b>——本对象可由领域事件反序列化而来，
 * 而事件流是长期资产，其合法性不能只依赖写入时刻的上游实现。
 * </p>
 *
 * @param itemCode  所属保全项编码（聚合据以推导批改类型）
 * @param amount    收付金额（恒为正的绝对值）
 * @param direction 收付方向（仅 DEBIT/CREDIT）
 * @param currency  币种（三字母代码，构造期归一为大写）
 * @param sourceRef 差额来源凭证
 */
public record PolicyMaintenanceAmountFact(
        String itemCode,
        BigDecimal amount,
        ChargeDirection direction,
        String currency,
        String sourceRef) {

    private static final Pattern CURRENCY_CODE = Pattern.compile("[A-Za-z]{3}");

    public PolicyMaintenanceAmountFact {
        itemCode = requireText(itemCode, "金额事实必须指明所属保全项");
        currency = requireText(currency, "金额事实的币种不能为空");
        sourceRef = requireText(sourceRef, "金额事实必须引用差额来源凭证");
        if (!CURRENCY_CODE.matcher(currency).matches()) {
            throw invalid("金额事实的币种必须是三字母代码");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw invalid("金额事实的金额必须大于零");
        }
        if (direction == null || direction == ChargeDirection.NONE) {
            throw invalid("金额事实必须给出明确的收付方向");
        }
        currency = currency.toUpperCase(Locale.ROOT);
    }

    /** 方向为借记（客户应付增加，如追加缴费）。 */
    public boolean debit() {
        return direction == ChargeDirection.DEBIT;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw invalid(message);
        }
        return value.trim();
    }

    private static PolicyBusinessRuleException invalid(String message) {
        return new PolicyBusinessRuleException(
                PolicyErrorCode.POLICY_MAINTENANCE_AMOUNT_INVALID, message);
    }
}
