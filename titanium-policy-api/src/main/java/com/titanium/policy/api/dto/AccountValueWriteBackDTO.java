package com.titanium.policy.api.dto;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 投资账户价值回写 DTO（跨域集成用，investment 域回写投连/万能保单账户价值）
 * <p>
 * 字段与 investment 域 Feign 请求体
 * {@code PolicyServiceClient.AccountValueWriteBackRequest(accountId, accountValue, currency)} 对齐。
 * </p>
 */
@Schema(description = "投资账户价值回写DTO")
@Data
public class AccountValueWriteBackDTO {

    @Schema(description = "投资账户ID", example = "ACC-2024-0001")
    private String accountId;

    @Schema(description = "最新账户价值金额", example = "12500.00")
    private BigDecimal accountValue;

    @Schema(description = "币种", example = "CNY")
    private String currency;
}
