package com.titanium.policy.api.model;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "金额DTO")
@Data
public class Amount {
    @Schema(description = "金额值", example = "1000.00")
    private BigDecimal value;
    @Schema(description = "货币", example = "USD")
    private String     currency;
}
