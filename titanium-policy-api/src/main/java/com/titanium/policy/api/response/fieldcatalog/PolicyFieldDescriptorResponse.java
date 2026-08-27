package com.titanium.policy.api.response.fieldcatalog;

import java.time.LocalDate;

import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldMaskingPolicy;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldObjectType;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldSensitivityLevel;
import com.titanium.metadata.enums.policy.fieldcatalog.PolicyFieldValueType;

import io.swagger.v3.oas.annotations.media.Schema;

/** Policy 稳定字段描述响应。 */
@Schema(description = "Policy 稳定字段描述")
public record PolicyFieldDescriptorResponse(
        @Schema(description = "稳定字段编码") String fieldCode,
        @Schema(description = "业务对象类型") PolicyFieldObjectType objectType,
        @Schema(description = "字段值类型") PolicyFieldValueType valueType,
        @Schema(description = "国际化标签键") String labelKey,
        @Schema(description = "是否属于集合对象") boolean collection,
        @Schema(description = "集合对象稳定标识字段") String objectIdentityField,
        @Schema(description = "字段能力") PolicyFieldCapabilityResponse capability,
        @Schema(description = "敏感级别") PolicyFieldSensitivityLevel sensitivity,
        @Schema(description = "掩码策略") PolicyFieldMaskingPolicy maskingPolicy,
        @Schema(description = "废弃日期") LocalDate deprecatedAt) {
}
