package com.titanium.policy.application.exception;

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

    public IssuanceOrchestrationException(String message, IssuanceResult partialResult, Throwable cause) {
        super(message, cause);
        this.partialResult = partialResult;
    }

    public IssuanceResult partialResult() {
        return partialResult;
    }
}
