package com.titanium.policy.web.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.titanium.metadata.enums.policy.PolicyForm;
import com.titanium.metadata.enums.product.ProductEnum.SalesChannel;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 创建保单请求
 * <p>
 * Web 层用例入参，经 {@code PolicyWebMapper} 转换为领域命令 {@code CreatePolicyCommand}，
 * 表现层不直接依赖领域命令。远程契约 {@code CreatePolicyDTO} 亦由 {@code PolicyApiProvider}
 * 转换为本 Request 后收敛到同一应用服务，保证远程与后台入口在应用层形态一致。
 * </p>
 */
@Schema(description = "创建保单请求")
@Data
public class CreatePolicyRequest {

    @Schema(description = "保单ID", example = "1234567890")
    private String        policyId;

    @Schema(description = "保单号", example = "P20230801001")
    private String        policyNo;

    @Schema(description = "关联投保单ID", example = "INS20230801001")
    private String        insuranceId;

    @Schema(description = "保单形态", example = "INDIVIDUAL")
    private PolicyForm    policyForm;

    @Schema(description = "产品ID", example = "PROD_A")
    private String        productId;

    @Schema(description = "签发机构", example = "ORG001")
    private String        issueOrg;

    @Schema(description = "投保人ID", example = "CUST001")
    private String        policyHolderId;

    @Schema(description = "被保险人ID", example = "CUST002")
    private String        insuredId;

    @Schema(description = "保额", example = "100000.00")
    private BigDecimal    sumInsured;

    @Schema(description = "保费", example = "1200.00")
    private BigDecimal    premium;

    @Schema(description = "币种", example = "CNY")
    private String        currency;

    @Schema(description = "生效日期", example = "2023-08-01T00:00:00")
    private LocalDateTime startDate;

    @Schema(description = "终止日期", example = "2023-12-31T23:59:59")
    private LocalDateTime endDate;

    @Schema(description = "销售渠道", example = "DIRECT")
    private SalesChannel  channel;

    @Schema(description = "租户ID", example = "tenant-001")
    private String        tenantId;
}
