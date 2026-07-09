package com.titanium.policy.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 保单状态 DTO（跨域集成用）
 * <p>
 * 面向其它微服务的保单状态查询响应，返回保单原生状态码（如 {@code EFFECTIVE}/{@code TERMINATED}），
 * 字段贴合调用方 Feign 契约，供其判定保单状态。
 * </p>
 */
@Schema(description = "保单状态DTO")
@Data
public class PolicyStatusDTO {

    @Schema(description = "保单ID", example = "1234567890")
    private String policyId;

    @Schema(description = "保单状态原生码", example = "EFFECTIVE")
    private String status;
}
