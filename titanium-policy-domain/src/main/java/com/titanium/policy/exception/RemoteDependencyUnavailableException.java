package com.titanium.policy.exception;

import com.titanium.metadata.errorcode.BaseErrorCode;
import com.titanium.metadata.exception.DomainException;

/**
 * 远程依赖不可用（m21-02 / 审查报告 RPC-01）。
 *
 * <p><b>为什么需要</b>：出单链路依赖产品域与条款域的实时数据。改造前适配器把「查询失败」
 * 折叠成 {@code null} / {@code List.of()}，与「确实没有数据」在类型上不可区分，
 * 于是等待期/犹豫期被置 0、保单缺条款缺责任，却**静默出单成功**
 * （监管：长期人身险的犹豫期是客户法定解约权；合同：保单缺条款即无理赔依据）。
 * </p>
 *
 * <p><b>语义契约</b>：本异常把两种结果在类型上分开——
 * </p>
 * <ul>
 * <li><b>确实没有</b>（产品未绑定条款、未配置该项）：由下游以<b>成功响应 + 空值</b>表达，
 * 适配器原样返回空；</li>
 * <li><b>查不到/查失败</b>（连接被拒、超时、下游 5xx、下游业务失败码）：一律抛本异常。</li>
 * </ul>
 * <p>
 * 🔴 因此<b>适配器不得再以 null / 空集合表示远程失败</b>——这是本条修复的核心约束。
 * </p>
 *
 * <p>命名与位置：报告建议该类型最终归属 {@code titanium-metadata}（EXC-01 的统一异常根），
 * 本批先落在 policy 域，待异常体系统一改造（m23）时上提。
 * </p>
 */
public class RemoteDependencyUnavailableException extends DomainException {

    /**
     * @param errorCode 错误码枚举（技术失败用 {@code SystemErrorCode.DEPENDENCY_SERVICE_ERROR}）
     * @param message   业务上下文（应含被查询的 id 与下游返回的原始码，便于诊断）
     */
    public RemoteDependencyUnavailableException(BaseErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * @param errorCode 错误码枚举
     * @param message   业务上下文
     * @param cause     根因（Feign 异常等）
     */
    public RemoteDependencyUnavailableException(BaseErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
