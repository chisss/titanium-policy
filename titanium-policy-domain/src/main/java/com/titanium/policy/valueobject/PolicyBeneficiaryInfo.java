package com.titanium.policy.valueobject;

import com.titanium.metadata.enums.customer.CustomerEnum.IdCardType;
import com.titanium.metadata.enums.customer.CustomerEnum.InsuranceRole;

/**
 * 受益人信息
 * @param beneficiaryId 受益人ID
 * @param customerId 客户ID
 * @param name 姓名
 * @param certType 证件类型
 * @param certNo 证件号
 * @param beneficiaryType 受益角色类型
 * @param beneficiaryRatio 受益比例
 */
public record PolicyBeneficiaryInfo(String beneficiaryId, String customerId, String name, IdCardType certType,
                                    String certNo, InsuranceRole beneficiaryType, double beneficiaryRatio) {
}
