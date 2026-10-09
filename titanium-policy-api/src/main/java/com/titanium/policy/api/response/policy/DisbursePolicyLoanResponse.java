package com.titanium.policy.api.response.policy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 保单贷款放款回执（跨域 Feign 出参，g04-03）。
 * <p>
 * 放款成功后回执贷款业务号与放款要素（本金/冻结利率/放款日期），供调用方（保全域）入账与
 * 留痕引用——回执字段与请求一致，消费方以 {@code loanId} 关联本次放款。
 * </p>
 */
@Schema(description = "保单贷款放款回执")
public record DisbursePolicyLoanResponse(
        @Schema(description = "贷款明细业务号")
        String loanId,

        @Schema(description = "保单ID")
        String policyId,

        @Schema(description = "放款本金")
        BigDecimal loanAmount,

        @Schema(description = "冻结利率")
        BigDecimal interestRate,

        @Schema(description = "放款日期")
        LocalDateTime loanDate) {
}
