package com.titanium.policy.web.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.axonframework.modelling.command.AggregateStreamCreationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.titanium.metadata.errorcode.BaseErrorCode;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.errorcode.SystemErrorCode;
import com.titanium.metadata.exception.DomainException;
import com.titanium.metadata.response.ApiResponse;

/**
 * 保单域 Web 统一异常处理器（全局兜底）
 * <p>
 * 承接 controller 与 api provider 抛出的业务/领域异常，统一转为 {@link ApiResponse} 响应体。
 * 缺此处理器时领域异常直穿到 servlet 容器，表现为 HTTP 500 + Spring 默认错误页，
 * errorCode 与 message 全部丢失（D-501-14 / D-501-23）。
 * </p>
 * <p>
 * HTTP 状态码在传输层表达（400/404/409/422/500），不进 {@code ApiResponse.code}
 * （业务错误码 ≠ HTTP 状态码，见根规约 §8.2）；失败工厂只接受 {@code BaseErrorCode} 强类型。
 * </p>
 */
@RestControllerAdvice(basePackages = "com.titanium.policy.web")
public class PolicyExceptionHandler {

    /**
     * 本域全部可用错误码：本域 {@link PolicyErrorCode} + 跨域通用 {@link SystemErrorCode}。
     * <p>
     * {@link DomainException} 只携带 8 位数字码串（{@code getErrorCode()}），故需反查枚举实例
     * 才能交给 {@code ApiResponse.error(BaseErrorCode, message)}。
     * </p>
     */
    private static final List<BaseErrorCode> DOMAIN_ERROR_CODES = domainErrorCodes();

    /**
     * 历史「裸串错误码」→ 本域业务码。
     * <p>
     * {@code DomainException} 的裸串构造器已 {@code @Deprecated}（根规约 §3.4.12 红线 19），
     * 但存量抛出点仍在用：{@code PolicyBusinessRuleException} 全部落 {@code BUSINESS_RULE_VIOLATION}、
     * {@code PolicyStatusTransitionException} 全部落 {@code ILLEGAL_STATE_TRANSITION}
     * （具体规则码在 message 内，如 {@code 违反业务规则[POLICY_RULE_VIOLATION]: …}）。
     * 不映射则这些异常只能兜底成 500，D-501-14 的实测现象即如此。
     * </p>
     */
    private static final Map<String, BaseErrorCode> LEGACY_ERROR_CODES = Map.of(
            "BUSINESS_RULE_VIOLATION", PolicyErrorCode.POLICY_RULE_VIOLATION,
            "ILLEGAL_STATE_TRANSITION", PolicyErrorCode.POLICY_STATUS_TRANSITION_ILLEGAL,
            "POLICY_FIELD_CATALOG_INVALID", SystemErrorCode.PARAM_INVALID);

    /**
     * 处理领域异常（业务规则违反、状态流转非法、字段目录校验等，均继承 {@link DomainException}）
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainException(DomainException exception) {
        BaseErrorCode errorCode = resolve(exception.getErrorCode())
                .orElse(SystemErrorCode.SYSTEM_ERROR);
        return ResponseEntity.status(statusFor(errorCode))
                .body(ApiResponse.error(errorCode, exception.getMessage()));
    }

    /**
     * 处理聚合标识重用异常（重复/并发创建同一聚合 id，Axon 事件存储唯一键拦截）
     * <p>
     * 语义是「重复提交/并发竞争」而非系统故障，映射 409 + 本域业务码，调用方据此可区分重试与报错。
     * </p>
     */
    @ExceptionHandler(AggregateStreamCreationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAggregateStreamCreationException(
            AggregateStreamCreationException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(PolicyErrorCode.POLICY_AGGREGATE_ALREADY_EXISTS, exception.getMessage()));
    }

    /**
     * 处理非法参数异常（存量裸抛的兜底，统一 400 + PARAM_INVALID）
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(SystemErrorCode.PARAM_INVALID, exception.getMessage()));
    }

    /**
     * 兜底处理未预期异常（500 + SYSTEM_ERROR）
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(SystemErrorCode.SYSTEM_ERROR, exception.getMessage()));
    }

    /**
     * 按 8 位数字码反查枚举，未命中再按历史裸串码映射
     */
    private Optional<BaseErrorCode> resolve(String code) {
        return DOMAIN_ERROR_CODES.stream()
                .filter(errorCode -> errorCode.getCode().equals(code))
                .findFirst()
                .or(() -> Optional.ofNullable(LEGACY_ERROR_CODES.get(code)));
    }

    /**
     * 业务码 → HTTP 状态。
     * <p>
     * 命名约定：{@code *_NOT_EXIST} 资源不存在 → 404；规则违反 / 参数非法 → 422 / 400；
     * 状态流转冲突、聚合重复 → 409；{@code *_FAILED} 处理失败与其余无对应语义者 → 500。
     * </p>
     */
    private HttpStatus statusFor(BaseErrorCode errorCode) {
        if (errorCode instanceof SystemErrorCode systemErrorCode) {
            return switch (systemErrorCode) {
                case PARAM_INVALID, TENANT_HEADER_MISSING, TENANT_CONTEXT_ERROR -> HttpStatus.BAD_REQUEST;
                case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
                default -> HttpStatus.INTERNAL_SERVER_ERROR;
            };
        }
        if (errorCode instanceof PolicyErrorCode policyErrorCode) {
            if (policyErrorCode.name().endsWith("_NOT_EXIST")) {
                return HttpStatus.NOT_FOUND;
            }
            return switch (policyErrorCode) {
                case POLICY_RULE_VIOLATION -> HttpStatus.UNPROCESSABLE_CONTENT;
                case POLICY_STATUS_TRANSITION_ILLEGAL, POLICY_AGGREGATE_ALREADY_EXISTS, POLICY_ALREADY_EXIST ->
                    HttpStatus.CONFLICT;
                default -> policyErrorCode.name().endsWith("_FAILED")
                        ? HttpStatus.INTERNAL_SERVER_ERROR
                        : HttpStatus.BAD_REQUEST;
            };
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static List<BaseErrorCode> domainErrorCodes() {
        List<BaseErrorCode> codes = new ArrayList<>(List.of(PolicyErrorCode.values()));
        codes.addAll(List.of(SystemErrorCode.values()));
        return List.copyOf(codes);
    }
}
