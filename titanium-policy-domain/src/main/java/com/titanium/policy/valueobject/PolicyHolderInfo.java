package com.titanium.policy.valueobject;

/**
 * 保单投保人信息
 * <p>
 * 存储投保人的基本信息，如投保人ID、姓名、证件类型、证件号、手机号
 * </p>
 */
public record PolicyHolderInfo(String policyHolderId, String name, String certType, String certNo, String phone) {
}
