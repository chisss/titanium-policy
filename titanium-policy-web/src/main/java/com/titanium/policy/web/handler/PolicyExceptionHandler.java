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

import com.titanium.common.boundary.ErrorCategoryHttpStatus;
import com.titanium.metadata.errorcode.BaseErrorCode;
import com.titanium.metadata.errorcode.PolicyErrorCode;
import com.titanium.metadata.errorcode.RawErrorCode;
import com.titanium.metadata.errorcode.SystemErrorCode;
import com.titanium.metadata.exception.DomainException;
import com.titanium.metadata.response.ApiResponse;

import lombok.extern.slf4j.Slf4j;

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
 * <p>
 * 🔴 <b>本类只处理业务/领域异常；框架异常（报文不可解析、方法不支持、路径未命中、
 * 缺请求头等）统一由 {@code titanium-common} 的 {@code BoundaryExceptionHandler} 接管</b>——
 * 后者以 {@code HIGHEST_PRECEDENCE + 20} 注册，在派发上<b>先于</b>本类命中框架异常。
 * 本类曾自带的 {@code handleMissingRequestHeader}（R8-05：缺 {@code X-Tenant-Id} → 400 +
 * {@code TENANT_HEADER_MISSING}）已随之<b>不可达</b>，故删除；该语义迁至
 * {@code FrameworkErrorCodes}（全域唯一实现），行为不变，且同时覆盖了 {@code /web/v1/**}
 * 这条 {@code TenantApiInterceptor} 不覆盖的入口。
 * </p>
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.titanium.policy.web")
public class PolicyExceptionHandler {

    /**
     * 本域全部可用错误码：本域 {@link PolicyErrorCode} + 跨域通用 {@link SystemErrorCode}。
     * <p>
     * 🔴 自 2026-09-25 起，{@link DomainException} 的错误码字段已是 {@link BaseErrorCode} 强类型，
     * <b>不再需要</b>按码串反查枚举——枚举身份随异常一路传递。本清单<b>仅</b>服务于存量裸串路径
     * （{@link RawErrorCode} 包装的调用点，构造器已 {@code @Deprecated}）：它只带码串、无枚举身份，
     * 仍需回查才能恢复 404/409 等状态语义。EXC-06 废弃棘轮清零后本清单即可删除。
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
     * 「按 id 取详情」未命中时的统一拒绝（R8-05）。
     * <p>
     * 🔴 <b>为何文案把两种成因合二为一</b>：读模型查询一律带租户维度
     * （{@code findByXxxIdAndTenantId}），未命中时无法区分「该 id 根本不存在」与
     * 「存在但不属于当前租户」。这两种成因**不得分开报**——分开即等于提供侧信道：
     * 攻击者可逐个枚举 id，凭 403/404 的差异探测哪些 id 在别处真实存在。
     * 故隔离行为维持不变（仍是 404 + 相同的 body），只把「排除法线索」明说出来。</p>
     * <p>
     * 🔴 <b>为何不能继续返回空 body</b>：调用方（admin BFF）拿不到任何线索，只能回落到
     * 硬编码的「资源不存在」，运维据此排查时无从分辨「id 拼错了」与「租户错配」；
     * 而这两者的处置完全不同（前者改请求，后者查租户上下文）。</p>
     *
     * @param errorCode  本域 {@code *_NOT_EXIST} 业务码（statusFor 据此映射 404）
     * @param resourceId 请求的资源标识，回显以便与调用方手上的 id 直接对照
     */
    public static DomainException notFoundInTenant(BaseErrorCode errorCode, String resourceId) {
        return new DomainException(errorCode,
                errorCode.getMessage() + "，或不在当前租户可见范围内: " + resourceId);
    }

    /**
     * 处理领域异常（业务规则违反、状态流转非法、字段目录校验等，均继承 {@link DomainException}）
     * <p>
     * 🔴 状态码出自 {@link ErrorCategoryHttpStatus} 这张<b>全仓唯一</b>的「类别 → 状态」表，
     * 由错误码在其枚举里的 {@code category()} 决定，<b>不再有本类私有的命名推断</b>。
     * 迁移前本类另有一份 {@code statusFor}，按 {@code endsWith("_NOT_EXIST")} 之类的<b>名字</b>
     * 猜状态（{@code *_NOT_EXIST}→404、{@code *_FAILED}→500、余者 400），
     * 与 payment 域的那份各写一遍、互不一致；本次收敛为一处。
     * </p>
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Void>> handleDomainException(DomainException exception) {
        BaseErrorCode errorCode = resolve(exception.getErrorCode());
        return ResponseEntity.status(ErrorCategoryHttpStatus.of(errorCode))
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
     * 兜底处理未预期异常（500 + SYSTEM_ERROR；不泄漏堆栈与原始消息）
     * <p>
     * 🔴 本处理器有意接管 {@code IllegalArgumentException}/{@code IllegalStateException}（审查报告 EXC-05）：
     * 二者语义是<b>编程错误</b>而非「调用方参数错误」，故原 IAE→400 兜底已删除——它会把「服务端自己写错了」
     * 误报成「客户端传错了」。入参类错误已在边界由 Bean Validation 拦为 400（API-03），领域内的业务条件
     * 一律抛携带错误码的领域异常，故该路径上不再应有正常业务流量；真命中即说明存在未收敛的裸抛点。
     * </p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        // CWE-209：原始异常消息只进日志、不回显给调用方。原实现返回 exception.getMessage()，
        // 会把内部类名 / 包名 / SQL 片段 / 下游服务地址一并泄露，为攻击者提供侦察信息。
        log.error("[保单] 未处理异常，请求处理失败", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(SystemErrorCode.SYSTEM_ERROR, "系统内部错误，请稍后重试"));
    }

    /**
     * 领域错误码归一：强类型枚举直接透传；存量裸串码回查枚举，未命中再按历史符号码映射。
     * <p>
     * 返回非空——最终兜底 {@link SystemErrorCode#SYSTEM_ERROR}，调用方无需再判空。
     * </p>
     */
    private BaseErrorCode resolve(BaseErrorCode errorCode) {
        if (errorCode instanceof PolicyErrorCode || errorCode instanceof SystemErrorCode) {
            return errorCode;
        }
        // 存量裸串路径（@Deprecated 构造器）的语义保持：先按码串回查枚举（与改造前一致），
        // 再按历史符号码映射；两者皆未命中才降级为系统错误。
        return DOMAIN_ERROR_CODES.stream()
                .filter(candidate -> candidate.getCode().equals(errorCode.getCode()))
                .findFirst()
                .or(() -> Optional.ofNullable(LEGACY_ERROR_CODES.get(errorCode.getCode())))
                .orElse(SystemErrorCode.SYSTEM_ERROR);
    }

    private static List<BaseErrorCode> domainErrorCodes() {
        List<BaseErrorCode> codes = new ArrayList<>(List.of(PolicyErrorCode.values()));
        codes.addAll(List.of(SystemErrorCode.values()));
        return List.copyOf(codes);
    }
}
