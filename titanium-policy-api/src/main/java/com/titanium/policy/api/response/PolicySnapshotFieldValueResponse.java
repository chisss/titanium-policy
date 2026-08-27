package com.titanium.policy.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Policy 快照中的强类型规范字段值。 */
@Schema(description = "Policy 快照字段值")
public record PolicySnapshotFieldValueResponse(
        @Schema(description = "字段数据类型", example = "TEXT") String dataType,
        @Schema(description = "规范化文本；允许为 null") String canonicalValue,
        @Schema(description = "集合字段稳定业务对象ID；标量字段为 null") String objectId) {

    /** 兼容不含集合对象身份的标量字段客户端。 */
    public PolicySnapshotFieldValueResponse(String dataType, String canonicalValue) {
        this(dataType, canonicalValue, null);
    }
}
