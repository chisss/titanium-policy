package com.titanium.policy.api;

import java.time.LocalDate;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.titanium.metadata.response.ApiResponse;
import com.titanium.policy.api.response.fieldcatalog.PolicyFieldCatalogResponse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Policy 字段目录跨域只读契约。 */
@FeignClient(
        name = "titanium-policy",
        contextId = "policyFieldCatalogApi",
        path = "/api/v1/policy-field-catalogs")
public interface PolicyFieldCatalogApi {

    /** 查询指定业务时点生效的权威字段目录。 */
    @GetMapping("/current")
    ApiResponse<PolicyFieldCatalogResponse> getCurrentCatalog(
            @RequestHeader("X-Tenant-Id") @NotBlank String tenantId,
            @RequestParam(value = "productType", required = false) String productType,
            @RequestParam(value = "policyType", required = false) String policyType,
            @RequestParam("businessDate")
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    @NotNull
                    LocalDate businessDate);
}
