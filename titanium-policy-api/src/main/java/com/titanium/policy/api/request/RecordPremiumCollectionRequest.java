package com.titanium.policy.api.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Payment 或 Billing 回写首期保费收讫事实的正式跨域请求。 */
public record RecordPremiumCollectionRequest(
        @NotBlank @Size(max = 64) String paymentId,
        @NotBlank @Size(max = 64) String paymentNo,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal collectedAmount,
        @NotBlank @Size(max = 3) String currency,
        @NotBlank @Size(max = 32) String paymentMethod,
        @NotNull LocalDateTime collectedTime) {
}
