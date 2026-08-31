package com.titanium.policy.application.exception;

import com.titanium.metadata.errorcode.BaseErrorCode;

/**
 * 出单参与方解析失败异常。
 * <p>
 * 异常携带稳定业务码，边界层可据此返回明确的出单拒绝原因；禁止在解析失败时生成随机客户ID。
 * </p>
 */
public class CustomerResolutionException extends RuntimeException {

    private final String errorCode;
    private final BaseErrorCode errorCodeEnum;
    private final boolean retryable;

    /**
     * 携带错误码枚举构造（规约红线 16：异常必须携带 BaseErrorCode）。
     *
     * @param errorCode 错误码枚举
     * @param message   错误信息
     */
    public CustomerResolutionException(BaseErrorCode errorCode, String message) {
        this(errorCode, message, null, false);
    }

    /**
     * 携带错误码枚举构造（规约红线 16：异常必须携带 BaseErrorCode）。
     *
     * @param errorCode 错误码枚举
     * @param message   错误信息
     * @param cause     失败原因
     * @param retryable 是否可原样重试（瞬时故障为 true）
     */
    public CustomerResolutionException(BaseErrorCode errorCode, String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.errorCode = errorCode.getCode();
        this.errorCodeEnum = errorCode;
        this.retryable = retryable;
    }

    /**
     * @param errorCode 错误码
     * @param message   错误信息
     * @deprecated 裸字符串错误码无法国际化，新代码请改用
     *         {@link #CustomerResolutionException(BaseErrorCode, String, Throwable, boolean)}
     */
    @Deprecated
    public CustomerResolutionException(String errorCode, String message) {
        this(errorCode, message, null, false);
    }

    /**
     * @param errorCode 错误码
     * @param message   错误信息
     * @param cause     失败原因
     * @deprecated 裸字符串错误码无法国际化，新代码请改用
     *         {@link #CustomerResolutionException(BaseErrorCode, String, Throwable, boolean)}
     */
    @Deprecated
    public CustomerResolutionException(String errorCode, String message, Throwable cause) {
        this(errorCode, message, cause, false);
    }

    /**
     * @param errorCode 错误码
     * @param message   错误信息
     * @param cause     失败原因
     * @param retryable 是否可原样重试
     * @deprecated 裸字符串错误码无法国际化，新代码请改用
     *         {@link #CustomerResolutionException(BaseErrorCode, String, Throwable, boolean)}
     */
    @Deprecated
    public CustomerResolutionException(String errorCode, String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.errorCode = errorCode;
        this.errorCodeEnum = null;
        this.retryable = retryable;
    }

    public String errorCode() {
        return errorCode;
    }

    /**
     * 错误码枚举（仅通过 {@link BaseErrorCode} 构造时非空）。
     */
    public BaseErrorCode errorCodeEnum() {
        return errorCodeEnum;
    }

    public boolean retryable() {
        return retryable;
    }
}
