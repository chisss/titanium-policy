package com.titanium.policy.application.exception;

import com.titanium.metadata.errorcode.BaseErrorCode;
import com.titanium.policy.valueobject.IssuanceResult;

/**
 * 出单编排异常。
 * <p>
 * 当起点命令已经成功创建意向单或投保单、后续推进命令失败时，携带已落地的部分结果，避免应用层
 * 把真实存在的单据误记为拒保。
 * </p>
 */
public class IssuanceOrchestrationException extends RuntimeException {

    private final IssuanceResult partialResult;

    /**
     * 携带错误码枚举构造（规约红线 16：异常必须携带 BaseErrorCode，禁止裸串 code）。
     *
     * @param errorCode     错误码枚举
     * @param partialResult 已落地的部分出单结果
     * @param cause         失败原因
     */
    public IssuanceOrchestrationException(BaseErrorCode errorCode, IssuanceResult partialResult, Throwable cause) {
        this(errorCode.getMessage(), partialResult, cause);
    }

    /**
     * @param message       错误信息
     * @param partialResult 已落地的部分出单结果
     * @param cause         失败原因
     * @deprecated 裸字符串消息无法国际化，新代码请改用
     *         {@link #IssuanceOrchestrationException(BaseErrorCode, IssuanceResult, Throwable)}
     */
    @Deprecated
    public IssuanceOrchestrationException(String message, IssuanceResult partialResult, Throwable cause) {
        super(message, cause);
        this.partialResult = partialResult;
    }

    public IssuanceResult partialResult() {
        return partialResult;
    }
}
