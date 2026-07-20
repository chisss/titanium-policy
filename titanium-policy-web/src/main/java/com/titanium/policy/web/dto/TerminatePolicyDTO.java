package com.titanium.policy.web.dto;

import com.titanium.metadata.enums.policy.PolicyEnum;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 终止/退保请求
 * <p>
 * 承载终止原因说明与终止原因分类，经 {@code PolicyApplicationService} 的 Web 入口重载构造
 * {@code TerminatePolicyCommand}，表现层不直接依赖领域命令。
 * </p>
 */
@Schema(description = "终止/退保请求")
@Data
public class TerminatePolicyDTO {

    @Schema(description = "终止原因说明", example = "客户申请退保")
    private String                       reason;

    @Schema(description = "终止原因分类", example = "WITHDRAWAL")
    private PolicyEnum.TerminationReason terminationReason;
}
