package com.titanium.policy.web.dto.member;

import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;
import com.titanium.metadata.enums.customer.CustomerEnum.IdCardType;
import com.titanium.policy.common.enums.FamilyRelation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增被保险人请求（前端 → 后端，团单加保 / 家庭险增员，G12/g12-06）
 * <p>
 * 经 {@code PolicyWebMapper} 转换为 {@code AddInsuredMemberCommand}（操作人/租户 ID 取请求头）。
 * 家庭关系直接用域枚举 {@link FamilyRelation}，镜像 {@code ApplyEndorsementDTO.updateType} 直映射约定；
 * 远程契约版本（api 层）因零内部依赖改用 code 字符串。
 * </p>
 */
@Schema(description = "新增被保险人请求（团单加保 / 家庭险增员）")
@Data
public class AddInsuredMemberDTO {

    @Schema(description = "被保险人ID（1-32 位字母/数字或 . _ -，作为减员定位键）", example = "INS20261010001")
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9._-]{1,32}", message = "被保险人ID须为1-32位字母/数字或._-")
    private String         insuredId;

    @Schema(description = "客户主数据ID（引用 customer 域）", example = "CUST20261010001")
    @NotBlank
    private String         customerId;

    @Schema(description = "姓名（加保时点快照）", example = "张三")
    @NotBlank
    @Size(max = 64)
    private String         name;

    @Schema(description = "证件类型", example = "CHINA_ID_CARD")
    @NotNull
    private IdCardType     certType;

    @Schema(description = "证件号", example = "110101199001011234")
    @NotBlank
    @Size(max = 64)
    private String         certNo;

    @Schema(description = "年龄（参与费率推导）", example = "35")
    @NotNull
    @Min(0)
    @Max(150)
    private Integer        age;

    @Schema(description = "性别", example = "MALE")
    @NotNull
    private CustomerGender gender;

    @Schema(description = "手机号", example = "13800138000")
    @Size(max = 32)
    private String         phone;

    @Schema(description = "与投保人关系", example = "SELF")
    @Size(max = 32)
    private String         relationToHolder;

    @Schema(description = "家庭成员关系（家庭险必填、团单不填）", example = "CHILD")
    private FamilyRelation familyRelation;
}
