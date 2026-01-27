package com.titanium.policy.entity.proposal;

import java.util.regex.Pattern;

/**
 * 投保意向单申请人实体
 * <p>
 * 记录初步申请人信息，包括姓名、证件类型、证件号、联系方式等
 * </p>
 */
public record ProposalHolder(
        /**
         * 申请人ID，聚合内唯一
         */
        String applicantId,
        /**
         * 姓名
         */
        String name,
        /**
         * 证件类型
         */
        String certType,
        /**
         * 证件号
         */
        String certNo,
        /**
         * 联系方式
         */
        String phone,
        /**
         * 是否为被保险人
         */
        boolean isInsured
) {
    /**
     * 轻量校验申请人身份格式
     * <p>
     * 仅执行格式校验，不调用客户域强校验
     * </p>
     *
     * @return 校验结果，true表示校验通过
     */
    public boolean verifyApplicantInfo() {
        // 姓名不能为空
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        // 证件类型和证件号不能为空
        if (certType == null || certType.trim().isEmpty() || certNo == null || certNo.trim().isEmpty()) {
            return false;
        }
        // 手机号格式校验
        if (phone != null && !Pattern.matches("^1[3-9]\\d{9}$", phone)) {
            return false;
        }
        return true;
    }
}