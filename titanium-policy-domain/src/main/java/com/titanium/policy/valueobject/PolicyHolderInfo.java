package com.titanium.policy.valueobject;

import com.titanium.metadata.enums.customer.CustomerEnum.IdCardType;

/**
 * 保单投保人信息
 * <p>
 * 存储投保人的基本信息，如投保人ID、姓名、证件类型、证件号、手机号
 * </p>
 */
public record PolicyHolderInfo(String policyHolderId, String name, IdCardType certType, String certNo, String phone) {
}
