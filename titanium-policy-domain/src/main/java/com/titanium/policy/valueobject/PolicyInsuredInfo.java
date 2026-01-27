package com.titanium.policy.valueobject;

/**
 * 被保险人信息
 * <p>
 * 定义被保险人的基本信息，包括被保险人ID、客户ID、姓名、证件类型、证件号、年龄和性别
 * </p>
 */
public record PolicyInsuredInfo(String insuredId, String customerId, String name, String certType, String certNo, int age,
                                String gender) {
}
