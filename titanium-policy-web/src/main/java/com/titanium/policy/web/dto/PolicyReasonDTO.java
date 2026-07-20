package com.titanium.policy.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 保单状态变更通用请求（仅承载原因）
 * <p>
 * 用于中止/恢复/撤销等只需变更原因的保单状态流转入口。经 {@code PolicyApplicationService}
 * 的 Web 入口重载构造对应领域命令，表现层不直接依赖领域命令。
 * </p>
 */
@Schema(description = "保单状态变更通用请求")
@Data
public class PolicyReasonDTO {

    @Schema(description = "变更原因", example = "客户申请")
    private String reason;
}
