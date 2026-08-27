package com.titanium.policy.application.exception;

/**
 * 出单参与方解析失败异常。
 * <p>
 * 异常携带稳定业务码，边界层可据此返回明确的出单拒绝原因；禁止在解析失败时生成随机客户ID。
 * </p>
 */
public class CustomerResolutionException extends RuntimeException {

    private final String errorCode;
    private final boolean retryable;

    public CustomerResolutionException(String errorCode, String message) {
        this(errorCode, message, null, false);
    }

    public CustomerResolutionException(String errorCode, String message, Throwable cause) {
        this(errorCode, message, cause, false);
    }

    public CustomerResolutionException(String errorCode, String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public String errorCode() {
        return errorCode;
    }

    public boolean retryable() {
        return retryable;
    }
}
