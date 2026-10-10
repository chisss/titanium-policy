package com.titanium.policy.api.request.member;

import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;
import com.titanium.metadata.enums.customer.CustomerEnum.IdCardType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增被保险人请求（团单加保 / 家庭险增员，G12/g12-06 远程契约）
 * <p>
 * 契约形态与 {@code RecordPremiumCollectionRequest} 同法（record + JSR-303 紧凑注解）。
 * 🔴 api 模块零内部依赖：家庭成员关系以 <b>code 字符串</b>承载（{@code FamilyRelation} 枚举 code），
 * 由 provider 经 {@code PolicyWebMapper} 翻译为域枚举——此处禁止 import policy 内部类型。
 * </p>
 *
 * @param insuredId 被保险人ID（聚合内标识，1-32 位 [A-Za-z0-9._-]；合规时同时作为读模型行标识，
 *                  后续减员以它定位）
 * @param customerId 客户主数据ID（引用 customer 域）
 * @param name 姓名（加保时点快照）
 * @param certType 证件类型
 * @param certNo 证件号
 * @param age 年龄（参与费率推导）
 * @param gender 性别
 * @param phone 手机号（可空）
 * @param relationToHolder 与投保人关系（可空）
 * @param familyRelation 家庭成员关系码（家庭险必填、团单可空，取值见 FamilyRelation code）
 */
public record AddInsuredMemberRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9._-]{1,32}", message = "被保险人ID须为1-32位字母/数字或._-")
        String insuredId,
        @NotBlank String customerId,
        @NotBlank @Size(max = 64) String name,
        @NotNull IdCardType certType,
        @NotBlank @Size(max = 64) String certNo,
        @NotNull @Min(0) @Max(150) Integer age,
        @NotNull CustomerGender gender,
        @Size(max = 32) String phone,
        @Size(max = 32) String relationToHolder,
        @Size(max = 32) String familyRelation) {
}
