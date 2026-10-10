package com.titanium.policy.fieldcatalog;

import com.titanium.metadata.errorcode.BaseErrorCode;
import com.titanium.metadata.errorcode.SystemErrorCode;
import com.titanium.metadata.exception.DomainException;

/**
 * Policy 字段目录定义或查询条件非法。
 * <p>
 * 错误码默认 {@link SystemErrorCode#PARAM_INVALID}——与 web 边界 {@code PolicyExceptionHandler}
 * 原先经 {@code LEGACY_ERROR_CODES} 把符号串 {@code POLICY_FIELD_CATALOG_INVALID} 回查得到的枚举一致，
 * 故直接构造不改变对外码与状态。此前转发父类裸串构造器属降级路径（EXC-06）。
 * </p>
 * <p>
 * 🔴 <b>携带具体业务码的构造器</b>：目录的四条分析位不变量（G07/g07-03）各有专属错误码
 * （{@code PolicyErrorCode} 20010000-20010003），调用方据此区分修法，故不接受降级为
 * 通用 {@code PARAM_INVALID}。传 {@code null} 时回退默认码，避免出现「错误码为空」的异常。
 * </p>
 */
public class PolicyFieldCatalogValidationException extends DomainException {

    public PolicyFieldCatalogValidationException(String message) {
        super(SystemErrorCode.PARAM_INVALID, message);
    }

    /**
     * 携带具体业务错误码构造。
     *
     * @param errorCode 业务错误码；为 {@code null} 时回退 {@link SystemErrorCode#PARAM_INVALID}
     * @param message   兜底文案（对外文案由边界层按 Accept-Language 重新渲染）
     */
    public PolicyFieldCatalogValidationException(BaseErrorCode errorCode, String message) {
        super(errorCode == null ? SystemErrorCode.PARAM_INVALID : errorCode, message);
    }
}
