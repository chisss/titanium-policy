package com.titanium.policy.valueobject.customer;

import com.titanium.metadata.enums.customer.CustomerEnum.CustomerGender;
import com.titanium.metadata.enums.customer.CustomerEnum.IdCardType;

/**
 * 出单时用于解析客户主数据的身份快照。
 * <p>
 * 该类型只表达跨域调用所需的稳定业务字段，不暴露 customer 域的 DTO，避免保单域反向依赖
 * customer 的实现细节。
 * </p>
 */
public record CustomerIdentitySnapshot(String fullName, IdCardType idType, String idNo,
                                       CustomerGender gender, String phoneNumber, String operatorId) {
}
