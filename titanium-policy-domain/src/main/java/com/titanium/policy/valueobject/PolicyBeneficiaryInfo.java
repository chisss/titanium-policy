package com.titanium.policy.valueobject;

/**
 * 受益人信息
 * @param beneficiaryId
 * @param customerId
 * @param name
 * @param certType
 * @param certNo
 * @param beneficiaryType
 * @param beneficiaryRatio
 */
public record PolicyBeneficiaryInfo(String beneficiaryId, String customerId, String name, String certType, String certNo,
                                    String beneficiaryType, double beneficiaryRatio) {
}
