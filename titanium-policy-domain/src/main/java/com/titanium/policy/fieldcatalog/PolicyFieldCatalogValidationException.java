package com.titanium.policy.fieldcatalog;

import com.titanium.metadata.errorcode.SystemErrorCode;
import com.titanium.metadata.exception.DomainException;

/**
 * Policy 字段目录定义或查询条件非法。
 * <p>
 * 错误码固定为 {@link SystemErrorCode#PARAM_INVALID}——与 web 边界 {@code PolicyExceptionHandler}
 * 原先经 {@code LEGACY_ERROR_CODES} 把符号串 {@code POLICY_FIELD_CATALOG_INVALID} 回查得到的枚举一致，
 * 故直接构造不改变对外码与状态。此前转发父类裸串构造器属降级路径（EXC-06）。
 * </p>
 */
public class PolicyFieldCatalogValidationException extends DomainException {

    public PolicyFieldCatalogValidationException(String message) {
        super(SystemErrorCode.PARAM_INVALID, message);
    }
}
