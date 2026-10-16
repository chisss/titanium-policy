package com.titanium.policy.api.model;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 金额 DTO（数值 + 币种）——policy 对外契约存量形态
 *
 * @deprecated DSN-07 货币建模统一（m25-09，2026-10）：新代码禁止使用，一律改用
 *             {@link com.titanium.metadata.valueobject.Money}（金额 + 币种）。
 *             存量按「先小域后大域」分批迁移：maintenance → regulatory → policy → underwriting → claim。
 */
@Schema(description = "金额DTO")
@Data
@Deprecated(since = "2026-10", forRemoval = true)
public class Amount {
    @Schema(description = "金额值", example = "1000.00")
    private BigDecimal value;
    @Schema(description = "货币", example = "USD")
    private String     currency;
}
